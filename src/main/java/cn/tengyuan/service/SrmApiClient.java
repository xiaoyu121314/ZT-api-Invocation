package cn.tengyuan.service;

import cn.tengyuan.config.ConfigUtil;
import cn.tengyuan.dto.MeterReadSubmitRequest;
import cn.tengyuan.dto.RoomLookupRequest;
import cn.tengyuan.dto.RoomLookupResult;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 中天 SRM 对外接口客户端。
 *
 * <p>接口文档声明无需认证，因此这里只设置 JSON Content-Type；如果后续增加鉴权，
 * 应统一在 {@link #createJsonHeaders()} 中追加请求头。</p>
 */
@Slf4j
@Service
public class SrmApiClient {

    private static final String ROOM_LOOKUP_PATH = "/SrmApi/GetRoomByBuildingAndRoom";
    private static final String METER_READ_SUBMIT_PATH = "/SrmApi/SubmitMeterRead";

    /** 房间查询接口约定的业务成功码。 */
    private static final int ROOM_LOOKUP_SUCCESS_CODE = 200;

    /** 抄表记录提交接口约定的业务成功码；中天返回 100 才表示提交成功。 */
    private static final int METER_READ_SUBMIT_SUCCESS_CODE = 100;

    /**
     * 中天云防御会拒绝 Java 8 HttpURLConnection 默认发送的
     * {@code User-Agent: Java/1.8.0_131}。使用明确的应用标识，既能避免被网关
     * 当成不受支持的客户端，也方便对方按应用维度排查请求来源。
     */
    private static final String DEFAULT_USER_AGENT = "ZT-api-Invocation/1.0";

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 根据源系统楼宇名称和房号查询中天业务编码。
     *
     * @throws IllegalStateException 对方返回非 200 业务码或缺少必要编码时抛出
     */
    public RoomLookupResult queryRoom(String communityName, String roomName) {
        RoomLookupRequest request = new RoomLookupRequest(communityName, roomName);
        JSONObject response = post(ROOM_LOOKUP_PATH, request);
        validateSuccess(response, "查询中天房间", ROOM_LOOKUP_SUCCESS_CODE);

        JSONObject data = response.getJSONObject("data");
        String roomCode = data == null ? null : data.getString("RoomCode");
        String orgCode = data == null ? null : data.getString("OrgCode");
        if (!hasText(roomCode) || !hasText(orgCode)) {
            throw new IllegalStateException("查询中天房间成功，但响应缺少 RoomCode 或 OrgCode："
                    + response.toJSONString());
        }
        return new RoomLookupResult(roomCode, orgCode);
    }

    /**
     * 批量提交抄表记录。空集合不会发送请求。
     */
    public void submitMeterReads(List<MeterReadSubmitRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        List<MeterReadSubmitRequest> filteredRequests = new ArrayList<>();
        for (MeterReadSubmitRequest request : requests) {
            filteredRequests.add(request);
        }
        JSONObject response = post(METER_READ_SUBMIT_PATH, filteredRequests);
        // SubmitMeterRead 与房间查询接口的业务成功码不同：返回 code=100
        // 才能把当前批次计入 DynamicTask.submitInBatches 的成功数量。
        validateSuccess(response, "提交中天抄表记录", METER_READ_SUBMIT_SUCCESS_CODE);
        log.info("中天抄表接口提交成功，本批数量：{}", requests.size());
    }

    /**
     * 使用对方约定的 application/json 格式发送 POST，并将响应统一解析为 JSON。
     */
    private JSONObject post(String path, Object body) {
        String url = joinUrl(ConfigUtil.getRequired("srm.base-url"), path);
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(body), createJsonHeaders());
        // 在真正发起 HTTP 请求前打印最终拼接后的 URL，便于确认配置的基础地址
        // 与接口路径组合结果；统一放在 post 方法中，可覆盖本客户端的所有 SRM 请求。
        log.info("中天接口请求 URL：{}, 请求参数：{}", url, JSON.toJSONString(body));
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);
        if (response.getBody() == null || response.getBody().trim().isEmpty()) {
            throw new IllegalStateException("中天接口返回空响应，地址：" + url);
        }
        return JSON.parseObject(response.getBody());
    }

    private HttpHeaders createJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        // 不能依赖 JDK 8 HttpURLConnection 的默认 User-Agent；该默认值会被中天
        // 网关直接返回 403。允许通过 srm.user-agent 覆盖，默认使用本应用标识。
        headers.set(HttpHeaders.USER_AGENT,
                ConfigUtil.get("srm.user-agent", DEFAULT_USER_AGENT));
        return headers;
    }

    /**
     * 按具体接口约定校验响应中的业务成功码，而不是只依赖 HTTP 状态码。
     *
     * <p>中天不同接口的成功码并不统一：房间查询使用 200，抄表记录提交使用
     * 100。因此成功码必须由调用方显式传入，避免后续新增接口时复用错误的
     * 全局成功码。</p>
     *
     * @param response 中天接口返回的 JSON 响应
     * @param operation 当前业务操作名称，用于异常信息定位
     * @param expectedCode 当前接口约定的业务成功码
     */
    private void validateSuccess(JSONObject response, String operation, int expectedCode) {
        Integer code = response.getInteger("code");
        if (code == null || code != expectedCode) {
            throw new IllegalStateException(operation + "失败，响应：" + response.toJSONString());
        }
    }

    private String joinUrl(String baseUrl, String path) {
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) + path
                : baseUrl + path;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
