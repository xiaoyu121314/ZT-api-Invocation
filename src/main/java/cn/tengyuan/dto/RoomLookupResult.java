package cn.tengyuan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 中天房间查询成功后返回的业务编码。 */
@Data
@AllArgsConstructor
public class RoomLookupResult {

    /** 中天房间唯一编码，后续抄表提交时使用。 */
    private String roomCode;

    /** 中天生活区/组织编码，后续抄表提交时使用。 */
    private String orgCode;
}
