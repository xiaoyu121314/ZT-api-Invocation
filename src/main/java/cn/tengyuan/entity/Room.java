package cn.tengyuan.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Room {

    /** 源建筑树中的房间节点 ID，用于查询该房间下的设备。 */

    private int id;

    /** 房间名称/房号，对应中天房间查询接口的 RoomName。 */
    private String name;

    /** 所属一级楼宇名称，对应中天接口文档中的 CommuntiyName。 */
    private String communityName;

    private BigDecimal money;

    private String password;

    private String number;

    private int subsidy;
}
