package tech.jxing.returnvision.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import tech.jxing.returnvision.model.entity.OperationLog;

/**
 * 【数据访问层】操作审计日志 Mapper 接口
 *
 * 职责：提供 operation_log 表的增删改查基础方法（继承 BaseMapper 自动实现）
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
