package cn.tengyuan.dto;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.math.BigDecimal;

/** 中天抄表记录提交项，对应 /SrmApi/SubmitMeterRead 数组中的单条数据。 */
@Data
public class MeterReadSubmitRequest {

    /** 中天房间编码，由房间查询接口返回。 */
    @JSONField(name = "RoomCode")
    private String roomCode;

    /** 抄表类型：0 冷水表、1 热水表、2 电表。 */
    @JSONField(name = "MeterReadType")
    private Integer meterReadType;

    /** 当前表头累计读数，对应源实时接口的 dbds 字段。 */
    @JSONField(name = "TotalActualUsage")
    private BigDecimal totalActualUsage;

    /** 抄表日期，格式为 yyyy-MM-dd。 */
    @JSONField(name = "CreateDateTime")
    private String createDateTime;

    /** 中天生活区/组织编码，由房间查询接口返回。 */
    @JSONField(name = "OrgCode")
    private String orgCode;
}
