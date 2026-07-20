package cn.tengyuan.task;

import cn.tengyuan.config.ConfigUtil;
import cn.tengyuan.dto.MeterReadSubmitRequest;
import cn.tengyuan.dto.RoomLookupResult;
import cn.tengyuan.entity.Room;
import cn.tengyuan.entity.SrmMeterreaddetail;
import cn.tengyuan.mapper.SrmMeterreaddetailMapper;
import cn.tengyuan.service.SrmApiClient;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 能耗抄表数据同步任务。
 *
 * <p>完整链路为：获取建筑树 -> 定位需要同步的房间 -> 获取房间下设备 ->
 * 获取设备实时累计读数 -> 查询中天房间编码 -> 分批提交抄表记录。</p>
 */
@Slf4j
@Configuration
@EnableScheduling
public class DynamicTask implements SchedulingConfigurer {

    /** 电表系统编号，调用实时数据接口时也作为 type 参数。 */
    private static final int ELECTRIC_SYSTEM_ID = 1;

    /** 水表系统编号，调用实时数据接口时也作为 type 参数。 */
    private static final int WATER_SYSTEM_ID = 2;

    /** 中天接口定义的冷水表类型。 */
    private static final int COLD_WATER_METER_TYPE = 0;

    /** 中天接口定义的热水表类型。 */
    private static final int HOT_WATER_METER_TYPE = 1;

    /** 中天接口定义的电表类型。 */
    private static final int ELECTRIC_METER_TYPE = 2;

    /** 源系统用属性 4 标识热水表。 */
    private static final int HOT_WATER_SOURCE_ATTRIBUTE = 4;

    /** 源系统用属性 5 标识冷水表。 */
    private static final int COLD_WATER_SOURCE_ATTRIBUTE = 5;

    private static final DateTimeFormatter SOURCE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SrmApiClient srmApiClient;

    @Autowired
    private SrmMeterreaddetailMapper srmMeterreaddetailMapper;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(
                this::synchronizeMeterReads,
                triggerContext -> {
                    String cron = ConfigUtil.getRequired("timer.cron");
                    return new CronTrigger(cron).nextExecutionTime(triggerContext);
                }
        );
    }

    /**
     * 执行一次完整同步。
     *
     * <p>单个房间或单个设备失败时只记录错误并跳过，避免一条异常数据阻断整栋楼；
     * 只有建筑树本身无法获取或解析时才终止本轮任务。</p>
     */
    private void synchronizeMeterReads() {
        // sync.enabled 控制采集和数据库落库，默认启用；对外接口提交由独立的
        // srm.submit-enabled 控制，避免为了防止外发而连本地数据也无法保存。
        if (!ConfigUtil.getBoolean("sync.enabled", true)) {
            log.info("抄表同步任务已通过 sync.enabled 配置关闭");
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("开始执行抄表同步任务，执行时间：{}", new Date());

        try {
            String buildingTreeJson = restTemplate.getForObject(
                    ConfigUtil.getRequired("build.url"), String.class);
            JSONObject root = JSON.parseObject(buildingTreeJson);
            List<Room> rooms = findRooms(root);

            List<MeterReadSubmitRequest> submitRequests = new ArrayList<>();
            int persistedCount = 0;
            for (Room room : rooms) {
                persistedCount += collectRoomMeterReads(room, submitRequests);
            }

            int submittedCount = 0;
            if (ConfigUtil.getBoolean("srm.submit-enabled", false)) {
                int batchSize = Math.max(1, ConfigUtil.getInt("sync.batch-size", 100));
                submittedCount = submitInBatches(submitRequests, batchSize);
            } else {
                log.info("抄表数据已落库；srm.submit-enabled=false，本轮不调用中天提交接口");
            }
            log.info("抄表同步任务结束：匹配房间 {} 个，成功落库 {} 条，成功提交 {} 条，耗时 {} ms",
                    rooms.size(), persistedCount, submittedCount,
                    System.currentTimeMillis() - startTime);
        } catch (Exception exception) {
            log.error("抄表同步任务执行失败，耗时 {} ms",
                    System.currentTimeMillis() - startTime, exception);
        }
    }

    /**
     * 采集单个房间下所有支持的仪表读数。
     *
     * @param room 当前房间，包含源系统房间 ID、房号和所属楼宇名称
     * @param result 本轮任务待提交的数据集合
     */
    private int collectRoomMeterReads(Room room, List<MeterReadSubmitRequest> result) {
        try {
            String deviceJson = restTemplate.getForObject(
                    ConfigUtil.getRequired("dev.url"), String.class, room.getId());
            JSONObject deviceResponse = JSON.parseObject(deviceJson);
            JSONArray systems = deviceResponse.getJSONArray("data");
            if (systems == null) {
                log.warn("房间未返回设备系统数据，楼宇：{}，房间：{}",
                        room.getCommunityName(), room.getName());
                return 0;
            }

            // 房间映射只影响中天接口字段，不应阻断源仪表数据落库。
            // 未配置目标地址或对方接口暂时不可用时，仍保存源房号和仪表读数。
            RoomLookupResult targetRoom = queryTargetRoomSafely(room);
            int persistedCount = 0;
            for (int i = 0; i < systems.size(); i++) {
                JSONObject system = systems.getJSONObject(i);
                Integer systemId = system.getInteger("sysid");
                if (systemId == null ||
                        (systemId != ELECTRIC_SYSTEM_ID && systemId != WATER_SYSTEM_ID)) {
                    continue;
                }

                JSONArray devices = system.getJSONArray("device");
                if (devices == null) {
                    continue;
                }
                for (int j = 0; j < devices.size(); j++) {
                    JSONObject device = devices.getJSONObject(j);
                    if (collectDeviceMeterRead(systemId, device, room, targetRoom, result)) {
                        persistedCount++;
                    }
                }
            }
            return persistedCount;
        } catch (Exception exception) {
            log.error("采集房间抄表数据失败，已跳过。楼宇：{}，房间：{}，源房间ID：{}",
                    room.getCommunityName(), room.getName(), room.getId(), exception);
            return 0;
        }
    }

    /**
     * 尝试查询中天房间编码，但不让目标系统故障影响本地落库。
     *
     * @return 查询成功时返回中天编码；未配置地址或查询失败时返回 null
     */
    private RoomLookupResult queryTargetRoomSafely(Room room) {
        String baseUrl = ConfigUtil.get("srm.base-url");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            log.debug("未配置 srm.base-url，房间 {} 本轮仅保存本地数据库", room.getName());
            return null;
        }
        try {
            return srmApiClient.queryRoom(room.getCommunityName(), room.getName());
        } catch (Exception exception) {
            log.warn("中天房间编码查询失败，本房间仍继续落库。楼宇：{}，房间：{}，原因：{}",
                    room.getCommunityName(), room.getName(), exception.getMessage());
            return null;
        }
    }

    /**
     * 获取单个设备的最新累计读数，并转换为中天提交格式。
     *
     * <p>源接口字段含义：dbds 为当前表头累计读数，usetime 为设备数据时间，
     * shuxing 在水表系统中区分冷热水。电表直接依据 sysid=1 判定。</p>
     */
    private boolean collectDeviceMeterRead(Integer systemId,
                                           JSONObject device,
                                           Room room,
                                           RoomLookupResult targetRoom,
                                           List<MeterReadSubmitRequest> result) {
        String deviceId = device.getString("id");
        if (deviceId == null || deviceId.trim().isEmpty()) {
            log.warn("设备缺少 id，跳过设备数据：{}", device.toJSONString());
            return false;
        }

        try {
            String realTimeJson = restTemplate.getForObject(
                    ConfigUtil.getRequired("realTimeInfo.url"),
                    String.class, systemId, deviceId);
            // 实时读数接口返回的是 JSON 数组，即使只有一个设备，最外层仍然是中括号。
            JSONArray realTimeArray = JSON.parseArray(realTimeJson);

            // 防止接口返回 null 或空数组，避免获取第一条数据时发生下标越界异常。
            if (realTimeArray == null || realTimeArray.isEmpty()) {
                log.warn("设备实时读数为空，已跳过。设备ID：{}，系统类型：{}",
                        deviceId, systemId);
                return false;
            }

            // 当前请求是根据设备 ID 查询实时读数，正常情况下数组中只有一条数据。
            JSONObject realTime = realTimeArray.getJSONObject(0);

            Integer meterReadType = resolveMeterReadType(systemId, realTime.getInteger("shuxing"));
            if (meterReadType == null) {
                log.warn("无法识别仪表类型，跳过设备。设备ID：{}，sysid：{}，shuxing：{}",
                        deviceId, systemId, realTime.getInteger("shuxing"));
                return false;
            }

            BigDecimal totalActualUsage = realTime.getBigDecimal("dbds");
            if (totalActualUsage == null) {
                log.warn("设备未返回累计读数 dbds，跳过设备。设备ID：{}", deviceId);
                return false;
            }

            // 只有数据库写入成功后才加入待提交列表，保证本地库是本轮推送数据的
            // 可追溯依据；落库异常会进入外层 catch，本条数据不会继续外发。
            persistMeterRead(realTime, device, deviceId, room, targetRoom,
                    meterReadType, totalActualUsage);

            // 只有拿到对方 RoomCode/OrgCode 的记录才具备提交条件；未映射记录
            // 已经正常保存在数据库，后续映射恢复后会在下一轮更新并进入提交列表。
            if (targetRoom != null) {
                MeterReadSubmitRequest request = new MeterReadSubmitRequest();
                request.setRoomCode(targetRoom.getRoomCode());
                request.setOrgCode(targetRoom.getOrgCode());
                request.setMeterReadType(meterReadType);
                request.setTotalActualUsage(totalActualUsage);
                request.setCreateDateTime(resolveReadingDate(realTime.getString("usetime")));
                result.add(request);
            }
            return true;
        } catch (Exception exception) {
            log.error("获取设备实时读数失败，已跳过。设备ID：{}，系统类型：{}",
                    deviceId, systemId, exception);
            return false;
        }
    }

    /**
     * 将采集到的最新仪表累计读数写入 srm_meterreaddetail。
     *
     * <p>首次采集时创建记录，并以当前累计读数作为初始基线；后续采集按唯一仪表
     * 编码更新同一行，上期总用量取数据库原累计读数，本期实际用量为新旧累计读数
     * 之差。这样既能保存最新读数，也不会因当前秒级定时配置不断插入重复记录。</p>
     */
    private void persistMeterRead(JSONObject realTime,
                                  JSONObject device,
                                  String deviceId,
                                  Room room,
                                  RoomLookupResult targetRoom,
                                  Integer meterReadType,
                                  BigDecimal totalActualUsage) {
        String meterCode = realTime.getString("dbh");
        if (meterCode == null || meterCode.trim().isEmpty()) {
            meterCode = device.getString("dbh");
        }
        if (meterCode == null || meterCode.trim().isEmpty()) {
            // 个别设备没有表号时使用“系统类型-设备ID”作为稳定兜底编码，
            // 仍能保证同一设备后续更新同一条数据库记录。
            meterCode = meterReadType + "-" + deviceId;
        }

        SrmMeterreaddetail existing =
                srmMeterreaddetailMapper.selectSrmMeterreaddetailByCode(meterCode);
        SrmMeterreaddetail record = new SrmMeterreaddetail();
        record.setCode(meterCode);
        // 中天映射可用时保存其业务编码；不可用时至少保存源房号，保证采集数据
        // 不因外部接口故障丢失。已有中天映射时继续保留原编码，防止目标接口
        // 短暂不可用导致数据库中的 UUID 被源房号覆盖。
        if (targetRoom != null) {
            record.setRoomCode(targetRoom.getRoomCode());
            record.setOrgCode(targetRoom.getOrgCode());
        } else {
            record.setRoomCode(existing == null || existing.getRoomCode() == null
                    ? room.getName() : existing.getRoomCode());
            record.setOrgCode(existing == null ? null : existing.getOrgCode());
        }
        record.setMeterReadType(meterReadType);
        record.setTotalActualUsage(totalActualUsage);
        record.setCreateDateTime(resolveReadingTime(realTime.getString("usetime")));

        if (existing == null) {
            // 第一条数据没有可比较的上期值，因此将当前读数作为基线，
            // 本期实际用量记为 0；下一次采集即可得到准确差值。
            record.setUpperTotalActualUsage(totalActualUsage);
            record.setActualUsage(BigDecimal.ZERO);
            int affectedRows = srmMeterreaddetailMapper.insertSrmMeterreaddetail(record);
            if (affectedRows != 1) {
                throw new IllegalStateException("新增抄表数据失败，仪表编码：" + meterCode);
            }
            log.info("新增抄表数据成功，仪表编码：{}，累计读数：{}", meterCode, totalActualUsage);
            return;
        }

        BigDecimal previousTotal = existing.getTotalActualUsage();
        if (previousTotal == null) {
            previousTotal = totalActualUsage;
        }
        record.setId(existing.getId());
        record.setUpperTotalActualUsage(previousTotal);
        record.setActualUsage(totalActualUsage.subtract(previousTotal));
        int affectedRows = srmMeterreaddetailMapper.updateSrmMeterreaddetail(record);
        if (affectedRows != 1) {
            throw new IllegalStateException("更新抄表数据失败，仪表编码：" + meterCode);
        }
        log.info("更新抄表数据成功，仪表编码：{}，上期读数：{}，当前读数：{}",
                meterCode, previousTotal, totalActualUsage);
    }

    /**
     * 将源系统分类转换成中天接口的抄表类型。
     */
    private Integer resolveMeterReadType(Integer systemId, Integer sourceAttribute) {
        if (systemId == ELECTRIC_SYSTEM_ID) {
            return ELECTRIC_METER_TYPE;
        }
        if (systemId == WATER_SYSTEM_ID && sourceAttribute != null) {
            if (sourceAttribute == HOT_WATER_SOURCE_ATTRIBUTE) {
                return HOT_WATER_METER_TYPE;
            }
            if (sourceAttribute == COLD_WATER_SOURCE_ATTRIBUTE) {
                return COLD_WATER_METER_TYPE;
            }
        }
        return null;
    }

    /**
     * 优先使用源设备数据时间中的日期；格式异常或缺失时使用服务器当前日期。
     */
    private String resolveReadingDate(String sourceTime) {
        if (sourceTime == null || sourceTime.trim().isEmpty()) {
            return LocalDate.now().toString();
        }
        try {
            return LocalDateTime.parse(sourceTime, SOURCE_TIME_FORMATTER)
                    .toLocalDate().toString();
        } catch (DateTimeParseException exception) {
            log.warn("设备数据时间格式异常，将使用当前日期。原始时间：{}", sourceTime);
            return LocalDate.now().toString();
        }
    }

    /**
     * 将源接口 yyyy-MM-dd HH:mm:ss 时间转换为数据库 Date；源时间缺失或异常时
     * 使用服务器当前时间，确保 create_date_time 始终能够落库。
     */
    private Date resolveReadingTime(String sourceTime) {
        if (sourceTime == null || sourceTime.trim().isEmpty()) {
            return new Date();
        }
        try {
            LocalDateTime localDateTime =
                    LocalDateTime.parse(sourceTime, SOURCE_TIME_FORMATTER);
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException exception) {
            log.warn("设备数据时间格式异常，数据库将使用当前时间。原始时间：{}", sourceTime);
            return new Date();
        }
    }

    /**
     * 按配置大小分批提交，避免一次报文包含过多房间和仪表记录。
     * 某一批失败不会影响后续批次继续提交。
     */
    private int submitInBatches(List<MeterReadSubmitRequest> requests, int batchSize) {
        int submittedCount = 0;
        for (int fromIndex = 0; fromIndex < requests.size(); fromIndex += batchSize) {
            int toIndex = Math.min(fromIndex + batchSize, requests.size());
            List<MeterReadSubmitRequest> batch = new ArrayList<>(
                    requests.subList(fromIndex, toIndex));
            try {
                srmApiClient.submitMeterReads(batch);
                submittedCount += batch.size();
            } catch (Exception exception) {
                log.error("提交抄表数据批次失败，范围：{}-{}，本批数量：{}",
                        fromIndex + 1, toIndex, batch.size(), exception);
            }
        }
        return submittedCount;
    }

    /**
     * 从建筑树中提取指定楼宇下的叶子房间。
     *
     * <p>源建筑树根节点下还包含其他项目，因此通过 sync.building-keyword
     * 限制同步范围；默认仅处理名称包含“中天光纤”的一级楼宇，防止误向中天接口
     * 提交其他项目的房间数据。</p>
     */
    private List<Room> findRooms(JSONObject root) {
        List<Room> rooms = new ArrayList<>();
        if (root == null) {
            return rooms;
        }

        String buildingKeyword = ConfigUtil.get("sync.building-keyword", "中天光纤");
        JSONArray topLevelNodes = normalizeChildren(root.get("children"));
        for (int i = 0; i < topLevelNodes.size(); i++) {
            JSONObject building = topLevelNodes.getJSONObject(i);
            String buildingName = building.getString("name");
            if (buildingName == null || !buildingName.contains(buildingKeyword)) {
                continue;
            }
            // 源系统楼宇名称为“中天光纤1号楼”，接口文档示例使用“1号楼”。
            // 通过可配置前缀完成名称对齐，避免写死在业务循环中。
            String targetBuildingName = normalizeTargetBuildingName(buildingName);
            collectLeafRooms(building, targetBuildingName, rooms);
        }
        return rooms;
    }

    /**
     * 将源系统楼宇名称转换为中天房间查询接口使用的名称。
     * 默认移除“中天光纤”前缀，例如“中天光纤1号楼”转换为“1号楼”；
     * 如果双方环境名称完全一致，可将 sync.building-name-remove-prefix 配置为空字符串。
     */
    private String normalizeTargetBuildingName(String sourceBuildingName) {
        String removablePrefix = ConfigUtil.get("sync.building-name-remove-prefix", "中天光纤");
        if (!removablePrefix.isEmpty() && sourceBuildingName.startsWith(removablePrefix)) {
            return sourceBuildingName.substring(removablePrefix.length()).trim();
        }
        return sourceBuildingName;
    }

    /**
     * 递归遍历楼宇节点，将没有有效子节点的节点作为房间。
     */
    private void collectLeafRooms(JSONObject node, String buildingName, List<Room> rooms) {
        JSONArray children = normalizeChildren(node.get("children"));
        if (children.isEmpty()) {
            Integer roomId = node.getInteger("id");
            String roomName = node.getString("name");
            if (roomId == null || roomName == null || roomName.trim().isEmpty()) {
                return;
            }

            Room room = new Room();
            room.setId(roomId);
            room.setName(roomName);
            room.setCommunityName(buildingName);
            room.setMoney(node.getBigDecimal("money") == null
                    ? BigDecimal.ZERO : node.getBigDecimal("money"));
            room.setPassword(node.getString("password") == null
                    ? "" : node.getString("password"));
            room.setNumber(node.getString("number") == null
                    ? "" : node.getString("number"));
            room.setSubsidy(node.getInteger("subsidy") == null
                    ? 0 : node.getInteger("subsidy"));
            rooms.add(room);
            return;
        }

        for (int i = 0; i < children.size(); i++) {
            collectLeafRooms(children.getJSONObject(i), buildingName, rooms);
        }
    }

    /**
     * 源接口部分无子节点数据会返回空白字符串，此处统一转换为空数组，
     * 避免直接强转 JSONArray 导致整轮任务失败。
     */
    private JSONArray normalizeChildren(Object childrenValue) {
        return childrenValue instanceof JSONArray ? (JSONArray) childrenValue : new JSONArray();
    }

    @PostConstruct
    public void verifyDatabaseConnection() {
        List<Map<String, Object>> databases = jdbcTemplate.queryForList("SELECT DATABASE() as db");
        log.info("数据库连接成功，当前数据库：{}", databases.get(0).get("db"));
    }
}
