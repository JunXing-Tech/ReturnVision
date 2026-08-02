package tech.jxing.returnvision.controller.dto;

/**
 * 普通用户注册请求
 *
 * 实现步骤：
 *   1. 用户名/密码/注册码均必填
 *   2. 注册码校验（未过期/未用尽/未失效）-> 拿 feishu_config_id
 *   3. 创建 sys_user(STAFF, feishu_config_id)
 *
 * 注：项目未引入 spring-boot-starter-validation，字段校验在 RegisterService 手动完成。
 */
public class StaffRegisterRequest {

    private String username;
    private String password;
    private String registerCode;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRegisterCode() { return registerCode; }
    public void setRegisterCode(String registerCode) { this.registerCode = registerCode; }
}
