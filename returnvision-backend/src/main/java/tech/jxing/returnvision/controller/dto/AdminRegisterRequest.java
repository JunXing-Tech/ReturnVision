package tech.jxing.returnvision.controller.dto;

/**
 * 公司管理员注册请求
 *
 * 实现步骤：
 *   1. 用户名/密码/公司名/飞书 app_id/app_secret 均必填
 *   2. 密码强度校验（最少 8 位含字母+数字，Service 层校验）
 *   3. 后端用 app_id/app_secret 调飞书验证 -> 自动建表 -> 创建 feishu_config + sys_user(ADMIN)
 *
 * 注：项目未引入 spring-boot-starter-validation，字段校验在 RegisterService 手动完成。
 */
public class AdminRegisterRequest {

    private String username;
    private String password;
    private String orgName;
    private String appId;
    private String appSecret;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
}
