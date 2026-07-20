package cn.tengyuan.mapper;

import java.util.List;
import cn.tengyuan.entity.SrmMeterreaddetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 能耗Mapper接口
 * 
 * @author MoShangHuaKai
 * @date 2026-06-04
 */
@Mapper
public interface SrmMeterreaddetailMapper
{
    /**
     * 按源设备/仪表编码查询当前落库记录。
     *
     * <p>code 在表设计中代表唯一仪表编码。定时任务通过该字段判断应执行
     * 首次插入还是更新最新累计读数，从而避免秒级调度产生重复数据。</p>
     *
     * @param code 源接口返回的仪表编码 dbh
     * @return 已存在的仪表记录；不存在时返回 null
     */
    SrmMeterreaddetail selectSrmMeterreaddetailByCode(@Param("code") String code);

    /**
     * 查询能耗
     * 
     * @param id 能耗主键
     * @return 能耗
     */
    public SrmMeterreaddetail selectSrmMeterreaddetailById(Long id);

    /**
     * 查询能耗列表
     * 
     * @param srmMeterreaddetail 能耗
     * @return 能耗集合
     */
    public List<SrmMeterreaddetail> selectSrmMeterreaddetailList(SrmMeterreaddetail srmMeterreaddetail);

    /**
     * 新增能耗
     * 
     * @param srmMeterreaddetail 能耗
     * @return 结果
     */
    public int insertSrmMeterreaddetail(SrmMeterreaddetail srmMeterreaddetail);

    /**
     * 修改能耗
     * 
     * @param srmMeterreaddetail 能耗
     * @return 结果
     */
    public int updateSrmMeterreaddetail(SrmMeterreaddetail srmMeterreaddetail);

    /**
     * 删除能耗
     * 
     * @param id 能耗主键
     * @return 结果
     */
    public int deleteSrmMeterreaddetailById(Long id);

    /**
     * 批量删除能耗
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSrmMeterreaddetailByIds(Long[] ids);
}
