package tech.jxing.returnvision.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import tech.jxing.returnvision.model.entity.SysRole;
import tech.jxing.returnvision.model.entity.SysUser;
import tech.jxing.returnvision.model.entity.SysUserRole;
import tech.jxing.returnvision.model.mapper.SysRoleMapper;
import tech.jxing.returnvision.model.mapper.SysUserMapper;
import tech.jxing.returnvision.model.mapper.SysUserRoleMapper;

/**
 * 【配置层】管理员账号初始化器
 *
 * 职责：应用启动时检查无 admin 账号则创建（密码 admin123，首次登录强制改密）
 * 层级：config 层
 * 关联：docs/05 第 4.5.8 节 admin 初始化策略
 *
 * 设计说明：
 *   schema.sql 只预置角色数据，admin 账号由此初始化器创建，
 *   避免 BCrypt 哈希值硬编码在 SQL 里导致不一致。
 */
@Configuration
@Slf4j
public class AdminInitializer {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_DISPLAY_NAME = "默认管理员";
    private static final String ADMIN_INITIAL_PASSWORD = "admin123";
    private static final String ADMIN_ROLE_CODE = "ADMIN";

    @Bean
    public ApplicationRunner adminInitializerRunner(SysUserMapper userMapper,
                                                    SysRoleMapper roleMapper,
                                                    SysUserRoleMapper userRoleMapper,
                                                    PasswordEncoder passwordEncoder) {
        return args -> {
            // 步骤1：检查是否已有 admin 账号
            Long existing = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, ADMIN_USERNAME));
            if (existing != null && existing > 0) {
                log.info("[Admin初始化] admin 账号已存在，跳过创建");
                return;
            }

            // 步骤2：创建 admin 用户
            SysUser admin = new SysUser();
            admin.setUsername(ADMIN_USERNAME);
            admin.setPasswordHash(passwordEncoder.encode(ADMIN_INITIAL_PASSWORD));
            admin.setDisplayName(ADMIN_DISPLAY_NAME);
            admin.setStatus("active");
            userMapper.insert(admin);

            // 步骤3：分配 ADMIN 角色
            SysRole adminRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, ADMIN_ROLE_CODE));
            if (adminRole == null) {
                log.error("[Admin初始化] ADMIN 角色不存在，请检查 schema.sql 是否预置");
                return;
            }

            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(admin.getId());
            userRole.setRoleId(adminRole.getId());
            userRoleMapper.insert(userRole);

            log.info("[Admin初始化] admin 账号已创建，初始密码={}，请尽快登录修改", ADMIN_INITIAL_PASSWORD);
        };
    }
}
