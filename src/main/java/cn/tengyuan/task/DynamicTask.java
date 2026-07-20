package cn.tengyuan.task;

import cn.tengyuan.config.ConfigUtil;
import cn.tengyuan.entity.Room;
import cn.tengyuan.entity.SrmMeterreaddetail;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.web.client.RestTemplate;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@EnableScheduling
public class DynamicTask implements SchedulingConfigurer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {

        registrar.addTriggerTask(
                () -> {
                    System.out.println("执行任务 - " + new Date());

                    String resultBuild = restTemplate.getForObject(
                            ConfigUtil.get("build.url"),
                            String.class
                    );

                    try {
                        JSONObject jsonObjectBuild = JSON.parseObject(resultBuild);
                        List<Room> rooms = findRooms(jsonObjectBuild);

//                        log.info("jsonObjectBuild返回结果: {}", JSON.toJSONString(jsonObjectBuild, true));


                        ArrayList<Integer> deviceIds = new ArrayList<>();
                        for (Room room : rooms) {
                            String resultDev = restTemplate.getForObject(
                                    ConfigUtil.get("dev.url"),
                                    String.class,
                                    room.getId()
                            );
                            try {
                                JSONObject jsonObjectDev = JSON.parseObject(resultDev);

//                                log.info("jsonObjectDev返回结果: {}", JSON.toJSONString(jsonObjectDev, true));

                                for (String key : jsonObjectDev.keySet()) {
                                    Object value = jsonObjectDev.get(key);
                                    if ("data".equals(key) && value instanceof JSONArray) {
                                        JSONArray dataArray = (JSONArray) value; // 强转成 JSONArray
                                        for (int i = 0; i < dataArray.size(); i++) {
                                            JSONObject dataObj = dataArray.getJSONObject(i); // 获取 JSONObject
                                            if (dataObj.getInteger("sysid") == 2) {
                                                JSONArray devices = (JSONArray) dataObj.get("device");
                                                for (int j = 0; j < devices.size(); j++) {
                                                    String resultWaterRealTimeInfo = restTemplate.getForObject(
                                                            ConfigUtil.get("realTimeInfo.url"),
                                                            String.class,
                                                            2,
                                                            devices.getJSONObject(j).getInteger("id")
                                                    );
                                                    try {
                                                        JSONArray waterRealTimeInfos = JSON.parseArray(resultWaterRealTimeInfo);

//                                                      log.info("jsonObjectRealTimeInfo返回结果: {}", JSON.toJSONString(jsonObjectRealTimeInfo, true));

                                                        for (int k = 0; k < waterRealTimeInfos.size(); k++) {
                                                            JSONObject waterRealTimeInfo = waterRealTimeInfos.getJSONObject(k);
                                                            SrmMeterreaddetail srmMeterreaddetail = new SrmMeterreaddetail();
                                                            if (waterRealTimeInfo.getInteger("shuxing") == 4) {
                                                                srmMeterreaddetail.setMeterReadType(1);
                                                            }
                                                            if (waterRealTimeInfo.getInteger("shuxing") == 5) {
                                                                srmMeterreaddetail.setMeterReadType(0);
                                                            }
                                                            srmMeterreaddetail.setCode(waterRealTimeInfo.getString("code"));
                                                        }
                                                    } catch (Exception e) {
                                                        log.error("jsonObjectRealTimeInfo解析失败，原始数据: {}", resultWaterRealTimeInfo, e);
                                                    }

                                                    deviceIds.add(devices.getJSONObject(j).getInteger("id"));
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.error("jsonObjectDev解析失败，原始数据: {}", resultDev, e);
                            }
                        }
                        ArrayList<SrmMeterreaddetail> objects = new ArrayList<>();
                        for (Integer deviceId : deviceIds) {
                            String resultWaterRealTimeInfo = restTemplate.getForObject(
                                    ConfigUtil.get("realTimeInfo.url"),
                                    String.class,
                                    2,
                                    deviceId
                            );
                            try {
                                JSONArray waterRealTimeInfos = JSON.parseArray(resultWaterRealTimeInfo);

//                                log.info("jsonObjectRealTimeInfo返回结果: {}", JSON.toJSONString(jsonObjectRealTimeInfo, true));

                                for (int i = 0; i < waterRealTimeInfos.size(); i++) {
                                    JSONObject waterRealTimeInfo = waterRealTimeInfos.getJSONObject(i);
                                    SrmMeterreaddetail srmMeterreaddetail = new SrmMeterreaddetail();
                                    if (waterRealTimeInfo.getInteger("shuxing") == 4) {
                                        srmMeterreaddetail.setMeterReadType(1);
                                    }
                                    if (waterRealTimeInfo.getInteger("shuxing") == 5) {
                                        srmMeterreaddetail.setMeterReadType(0);
                                    }
                                    srmMeterreaddetail.setCode(waterRealTimeInfo.getString("code"));
                                }
                            } catch (Exception e) {
                                log.error("jsonObjectRealTimeInfo解析失败，原始数据: {}", resultWaterRealTimeInfo, e);
                            }
                        }
                    } catch (Exception e) {
                        log.error("jsonObjectBuild解析失败，原始数据: {}", resultBuild, e);
                    }
                },
                triggerContext -> {

                    String cron = ConfigUtil.get("timer.cron");

                    return new CronTrigger(cron)
                            .nextExecutionTime(triggerContext);
                }
        );
    }

    /**
     * 递归遍历树状 JSONObject，直达最底层
     * @param node 当前处理的 JSON 节点
     */

    private List<Room> findRooms(JSONObject node) {
        List<Room> resultList = new ArrayList<>();

        if (node == null) {
            return resultList;
        }

        JSONArray children = node.getJSONArray("children");

        if (children == null || children.isEmpty()) {
            Room room = new Room();
            room.setId(node.getInteger("id") != null ? node.getInteger("id") : 0);
            room.setName(node.getString("name") != null ? node.getString("name") : "");
            room.setMoney(node.getBigDecimal("money") != null ? node.getBigDecimal("money") : BigDecimal.ZERO);
            room.setPassword(node.getString("password") != null ? node.getString("password") : "");
            room.setNumber(node.getString("number") != null ? node.getString("number") : "");
            room.setSubsidy(node.getInteger("subsidy") != null ? node.getInteger("subsidy") : 0);

//            log.info("【最底层房间】ID: {} | 名称: {} | 金额: {} | 密码: {} | 编号: {} | 补贴: {}",
//                    room.getId(), room.getName(), room.getMoney(), room.getPassword(), room.getNumber(), room.getSubsidy());

            resultList.add(room);
            return resultList;
        }

        for (int i = 0; i < children.size(); i++) {
            JSONObject child = children.getJSONObject(i);
            resultList.addAll(findRooms(child));
        }

        return resultList;
    }

    @PostConstruct
    public void initDeliveryOrderNoSeq() {
        List<Map<String, Object>> databases = jdbcTemplate.queryForList("SELECT DATABASE() as db");
        log.info("数据库连接成功！当前数据库：" + databases.get(0).get("db"));
    }
}
