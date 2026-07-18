package tech.jxing.returnvision.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * 【鉴权模块】JWT 工具类
 *
 * 职责：生成/解析/校验 access token
 * 层级：security 层
 * 调用方：AuthService（登录时生成）、JwtAuthenticationFilter（请求时解析）
 * 关联：docs/04 第 4.7.6 节 JWT 设计
 *
 * 设计要点：
 *   1. access token 用 JWT（无状态），refresh token 用随机字符串存库（有状态）
 *   2. secret 从环境变量读取，至少 32 字符
 *   3. payload 含 user_id / username / roles / type=access
 */
@Component
@Slf4j
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenExpiration;

    /**
     * 构造器注入
     *
     * @param secret                   JWT 签名密钥（jwt.secret）
     * @param accessTokenExpirationSec access token 有效期，秒（jwt.access-token-expiration）
     */
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration:7200}") long accessTokenExpirationSec) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpirationSec * 1000;
        log.info("[JWT] 初始化完成，access token 有效期={}秒", accessTokenExpirationSec);
    }

    /**
     * 生成 access token
     *
     * 实现步骤：
     *   1. 构造 payload（sub=username, userId, roles, type=access）
     *   2. 设置签发时间和过期时间
     *   3. 用 HS256 签名
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param roles    角色列表（如 ["ADMIN"]）
     * @return access token 字符串
     */
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        // 步骤1-3：构造并签名
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，返回 Claims
     *
     * 实现步骤：
     *   1. 解析并验签
     *   2. 校验 type=access
     *   3. 返回 Claims
     *
     * @param token access token 字符串
     * @return Claims，含 userId / roles / type 等
     * @throws io.jsonwebtoken.JwtException token 无效或过期时抛出
     */
    public Claims parseAccessToken(String token) {
        // 步骤1：解析验签
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // 步骤2：校验 type
        String type = claims.get("type", String.class);
        if (!"access".equals(type)) {
            throw new io.jsonwebtoken.JwtException("非 access token 类型：" + type);
        }

        // 步骤3：返回
        return claims;
    }

    /**
     * 获取 access token 剩余有效期（秒），用于响应 expires_in
     */
    public long getAccessTokenExpirationSec() {
        return accessTokenExpiration / 1000;
    }
}
