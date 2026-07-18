package tech.jxing.returnvision.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import tech.jxing.returnvision.model.entity.SysRole;

/**
 * 【数据访问层】角色 Mapper 接口
 *
 * 职责：提供 sys_role 表的增删改查基础方法（继承 BaseMapper 自动实现）
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
