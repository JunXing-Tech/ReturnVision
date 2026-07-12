package tech.jxing.returnvision.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import tech.jxing.returnvision.model.entity.ReturnRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 【数据访问层】退货记录 Mapper 接口
 *
 * 职责：提供 return_records 表的增删改查基础方法（继承 BaseMapper 自动实现）
 */
@Mapper
public interface ReturnRecordMapper extends BaseMapper<ReturnRecord> {
}
