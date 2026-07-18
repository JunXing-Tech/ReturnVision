package tech.jxing.returnvision.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 【鉴权模块】JWT 认证过滤器
 *
 * 职责：从请求 Header 解析 access token，校验后设置 SecurityContext
 * 层级：security 层（过滤器链，位于 UsernamePasswordAuthenticationFilter 之前）
 * 调用方：SecurityConfig 注册到过滤链
 *
 * 流程：
 *   1. 从 Authorization Header 提取 Bearer token
 *   2. 解析 token 得到 userId / username / roles
 *   3. 构造 AuthUser 设到 SecurityContext
 *   4. 放行请求；token 无效/缺失时不报错，留给后续权限判断处理
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 步骤1：提取 Bearer token
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        // 步骤2：解析 token
        try {
            Claims claims = jwtUtil.parseAccessToken(token);
            Long userId = claims.get("userId", Long.class);
            String username = claims.getSubject();
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);

            // 步骤3：构造 AuthUser 设到 SecurityContext
            if (userId != null && username != null && roles != null) {
                AuthUser authUser = new AuthUser(userId, username, null, true, roles);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(authUser, null, authUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException e) {
            // token 无效/过期，不设 SecurityContext，后续权限判断会返回 401
            log.debug("[JWT] token 解析失败：{}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        // 步骤4：放行请求
        filterChain.doFilter(request, response);
    }
}
