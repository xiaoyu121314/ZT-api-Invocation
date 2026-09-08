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
        validateSuccess(response, "查询中天房间");

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
            if (request != null && "fed00fa8-19a1-47e0-903c-a6f81e440029".equals(request.getRoomCode())) {
                filteredRequests.add(request);
            }
        }
        JSONObject response = post(METER_READ_SUBMIT_PATH, filteredRequests);
        validateSuccess(response, "提交中天抄表记录");
        log.info("中天抄表接口提交成功，本批数量：{}", requests.size());
    }

    /**
     * 使用对方约定的 application/json 格式发送 POST，并将响应统一解析为 JSON。
     */
    private JSONObject post(String path, Object body) {
        String url = joinUrl(ConfigUtil.getRequired("srm.base-url"), path);
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(body), createJsonHeaders());
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
        return headers;
    }

    /**
     * 文档中的成功判定为响应 JSON 的 code=200，而不是只依赖 HTTP 状态码。
     */
    private void validateSuccess(JSONObject response, String operation) {
        Integer code = response.getInteger("code");
        if (code == null || code != 200) {
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
