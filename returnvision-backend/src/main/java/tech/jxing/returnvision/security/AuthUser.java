package tech.jxing.returnvision.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 【鉴权模块】自定义 UserDetails
 *
 * 职责：封装已认证用户信息（含角色），供 Spring Security 上下文使用
 * 层级：security 层
 * 调用方：JwtAuthenticationFilter（解析 token 后构造）、AuthService（登录时构造）
 *
 * 设计要点：
 *   1. roles 存 role_code（如 ADMIN），Spring Security 权限表达式用 ROLE_ADMIN
 *   2. password 字段为 BCrypt 哈希，由 Spring Security 比对
 */
@Getter
public class AuthUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final boolean active;
    private final List<String> roles;

    public AuthUser(Long userId, String username, String passwordHash, boolean active, List<String> roles) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.active = active;
        this.roles = roles;
    }

    /**
     * 将 role_code 转为 Spring Security 的 GrantedAuthority
     * Spring Security 约定：hasRole('ADMIN') 自动加 ROLE_ 前缀，所以这里返回 ROLE_ADMIN
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
