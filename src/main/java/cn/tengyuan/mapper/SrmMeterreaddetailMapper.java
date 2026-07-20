package cn.tengyuan.mapper;

import java.util.List;
import cn.tengyuan.entity.SrmMeterreaddetail;

/**
 * 能耗Mapper接口
 * 
 * @author MoShangHuaKai
 * @date 2026-06-04
 */
public interface SrmMeterreaddetailMapper 
{
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
