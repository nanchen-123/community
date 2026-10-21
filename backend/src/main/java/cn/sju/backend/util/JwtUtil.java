package cn.sju.backend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private Long expiration;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token
     */
    public String generateToken(Long userId, String userName, String role, Long communityId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userName", userName)
                .claim("role", role)
                .claim("communityId", communityId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析 token
     */
    public Jws<Claims> parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token);
    }

    /**
     * 验证 token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 token 中获取用户 ID
     */
    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getPayload().getSubject());
    }

    /**
     * 从 token 中获取用户名
     */
    public String getUserName(String token) {
        return parseToken(token).getPayload().get("userName", String.class);
    }

    /**
     * 从 token 中获取角色
     */
    public String getRole(String token) {
        return parseToken(token).getPayload().get("role", String.class);
    }

    /**
     * 从 token 中获取小区 ID
     */
    public Long getCommunityId(String token) {
        Object value = parseToken(token).getPayload().get("communityId");
        return value == null ? null : Long.valueOf(value.toString());
    }
}
