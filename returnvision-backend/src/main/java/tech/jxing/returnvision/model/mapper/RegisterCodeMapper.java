package tech.jxing.returnvision.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import tech.jxing.returnvision.model.entity.RegisterCode;

/**
 * 【数据访问层】用户注册码 Mapper 接口
 *
 * 职责：提供 register_code 表的增删改查基础方法（继承 BaseMapper 自动实现）
 * 关联：docs/14 多租户改造 §3.6.2
 */
@Mapper
public interface RegisterCodeMapper extends BaseMapper<RegisterCode> {
}
