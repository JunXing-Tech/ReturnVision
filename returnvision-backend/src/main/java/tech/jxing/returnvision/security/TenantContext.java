package tech.jxing.returnvision.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 【鉴权模块】多租户上下文
 *
 * 职责：从 SecurityContext 取当前用户的 feishuConfigId，供租户插件/MetaObjectHandler 使用
 * 层级：security 层
 * 关联：docs/14 §3.3.1
 *
 * 设计要点：
 *   1. 从 SecurityContext 的 AuthUser 取 feishuConfigId
 *   2. null 表示平台级（平台 ADMIN，或后台定时任务无 SecurityContext）
 *   3. 租户插件 getTenantId() 返回 null 时不加 feishu_config_id 条件（平台级看全部）
 *   4. 后台定时任务（@Scheduled）无 SecurityContext，天然返回 null -> 不加条件 -> 清理所有租户
 */
public final class TenantContext {

    private TenantContext() {
    }

    /**
     * 获取当前用户的飞书配置ID
     *
     * 实现步骤：
     *   1. 从 SecurityContext 取 Authentication
     *   2. principal 是 AuthUser 时返回 feishuConfigId
     *   3. 否则返回 null（未登录/后台任务/旧 token 无 claim）
     *
     * @return feishuConfigId（null=平台级，非空=绑某公司）
     */
    public static Long currentFeishuConfigId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser)) {
            return null;
        }
        return ((AuthUser) authentication.getPrincipal()).getFeishuConfigId();
    }

    /**
     * 是否平台级（feishuConfigId 为 null）
     *
     * @return true=平台 ADMIN 或后台任务，查询不加租户条件
     */
    public static boolean isPlatform() {
        return currentFeishuConfigId() == null;
    }
}
