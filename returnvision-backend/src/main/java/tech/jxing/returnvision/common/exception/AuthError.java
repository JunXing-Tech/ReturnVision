package tech.jxing.returnvision.common.exception;

/**
 * 【公共模块】鉴权异常
 *
 * 错误码：1001 用户名或密码错误
 *         1003 refresh token 已失效或过期
 *         1004 飞书账号未绑定
 *         1005 账号已禁用
 *         1006 旧密码错误
 */
public class AuthError extends BizException {

    public AuthError(int code, String msg) {
        super(code, msg);
    }

    /** 1001 用户名或密码错误 */
    public static AuthError invalidCredentials() {
        return new AuthError(1001, "用户名或密码错误");
    }

    /** 1003 refresh token 已失效 */
    public static AuthError refreshTokenInvalid() {
        return new AuthError(1003, "refresh token 已失效，请重新登录");
    }

    /** 1004 飞书账号未绑定 */
    public static AuthError feishuNotBound() {
        return new AuthError(1004, "飞书账号未绑定，请联系管理员绑定");
    }

    /** 1005 账号已禁用 */
    public static AuthError accountDisabled() {
        return new AuthError(1005, "账号已禁用");
    }

    /** 1006 旧密码错误 */
    public static AuthError oldPasswordWrong() {
        return new AuthError(1006, "旧密码错误");
    }
}
