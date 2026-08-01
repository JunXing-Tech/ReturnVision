package tech.jxing.returnvision.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import tech.jxing.returnvision.security.TenantContext;

/**
 * 【配置层】多租户字段自动填充处理器
 *
 * 职责：insert 时自动从 TenantContext 取 feishuConfigId 填充到实体
 * 层级：config 层
 * 关联：docs/14 §3.3.1
 *
 * 设计要点：
 *   1. 仅对有 feishuConfigId 字段的实体生效（ReturnRecord / SysUser）
 *   2. 实体已显式 set 的值不覆盖（buildRecord 显式 set 优先）
 *   3. 后台任务无 SecurityContext 时填 null（平台级）
 */
@Component
@Slf4j
public class TenantMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 仅当实体有 feishuConfigId 字段且当前值为 null 时填充
        if (metaObject.hasSetter("feishuConfigId")) {
            Object existing = metaObject.getValue("feishuConfigId");
            if (existing == null) {
                Long cid = TenantContext.currentFeishuConfigId();
                metaObject.setValue("feishuConfigId", cid);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // update 不改 feishuConfigId（记录归属公司创建时定下，之后不变）
    }
}
