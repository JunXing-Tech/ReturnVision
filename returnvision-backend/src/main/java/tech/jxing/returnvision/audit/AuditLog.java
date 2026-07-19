package tech.jxing.returnvision.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 【审计模块】操作审计注解
 *
 * 职责：标记需要审计的方法，由 AuditAspect 切面自动记录审计日志
 * 层级：audit 层（注解）
 * 关联：docs/04 第 4.8.3 节
 *
 * 用法：
 *   @AuditLog(action = "LOGIN", targetType = "auth", description = "用户登录")
 *   public ResponseResult<...> login(...) { ... }
 *
 * 设计要点：
 *   1. 只标记方法，不侵入业务逻辑
 *   2. action / targetType / description 必填，recordResult 可选
 *   3. 切面在方法执行后记录（成功/失败都记录）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 操作类型：LOGIN/LOGOUT/UPLOAD/CONFIRM/DELETE_RECORD/CREATE_USER 等 */
    String action();

    /** 操作对象类型：return_record/user/auth 等 */
    String targetType() default "";

    /** 操作描述（人类可读） */
    String description() default "";

    /** 是否记录请求参数（默认 true，敏感接口可设为 false） */
    boolean recordParams() default true;
}
