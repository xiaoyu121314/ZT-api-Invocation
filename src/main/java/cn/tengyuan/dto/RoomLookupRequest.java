package cn.tengyuan.dto;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 中天房间查询请求。
 *
 * <p>CommuntiyName 是对方文档定义的字段拼写（包含该拼写错误），
 * 不能自行更正为 CommunityName，否则对方接口可能无法绑定参数。</p>
 */
@Data
@AllArgsConstructor
public class RoomLookupRequest {

    @JSONField(name = "CommuntiyName")
    private String communityName;

    @JSONField(name = "RoomName")
    private String roomName;
}
