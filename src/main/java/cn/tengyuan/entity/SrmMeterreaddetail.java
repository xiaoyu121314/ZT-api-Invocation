package cn.tengyuan.entity;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * 能耗对象 srm_meterreaddetail
 * 
 * @author MoShangHuaKai
 * @date 2026-06-04
 */
@Data
public class SrmMeterreaddetail {
    private static final long serialVersionUID = 1L;

    /** $column.columnComment */
    private Long id;

    /** 编码，唯一不可重复说明 */
    private String code;

    /** 房间号 */
    private String roomCode;

    /** 抄表类型 0 = 冷水表，1 = 热水表，2 = 电表 */
    private Integer meterReadType;

    /** 本期实际用量 */
    private BigDecimal actualUsage;

    /** 本期总用量 */
    private BigDecimal totalActualUsage;

    /** 上期总用量 */
    private BigDecimal upperTotalActualUsage;

    /** 抄表日期 */
    private Date createDateTime;

    /** 所属机构 */
    private String orgCode;

}
