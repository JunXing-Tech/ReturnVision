package tech.jxing.returnvision.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import tech.jxing.returnvision.model.entity.FeishuConfig;

/**
 * 【数据访问层】飞书多租户配置 Mapper 接口
 *
 * 职责：提供 feishu_config 表的增删改查基础方法（继承 BaseMapper 自动实现）
 * 关联：docs/14 多租户改造
 */
@Mapper
public interface FeishuConfigMapper extends BaseMapper<FeishuConfig> {
}
