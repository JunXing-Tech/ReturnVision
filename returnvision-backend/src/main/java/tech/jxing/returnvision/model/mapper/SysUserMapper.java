package tech.jxing.returnvision.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import tech.jxing.returnvision.model.entity.SysUser;

/**
 * 【数据访问层】用户 Mapper 接口
 *
 * 职责：提供 sys_user 表的增删改查基础方法（继承 BaseMapper 自动实现）
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
