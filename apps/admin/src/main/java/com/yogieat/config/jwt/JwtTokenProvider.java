package com.yogieat.config.jwt;

import com.yogieat.admin.domain.Admin;
import com.yogieat.admin.domain.value.AdminRole;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_LOGIN_ID = "loginId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;

    public static JwtTokenProvider of(String secret, long accessTokenValidity, long refreshTokenValidity) {
        return new JwtTokenProvider(JwtProperties.of(secret, accessTokenValidity, refreshTokenValidity));
    }

    public String createAccessToken(Admin admin) {
        return createToken(admin, TOKEN_TYPE_ACCESS, jwtProperties.accessTokenValidity());
    }

    public String createRefreshToken(Admin admin) {
        return createToken(admin, TOKEN_TYPE_REFRESH, jwtProperties.refreshTokenValidity());
    }

    public TokenPayload parseAccessToken(String token) {
        return parseToken(token, TOKEN_TYPE_ACCESS);
    }

    public TokenPayload parseRefreshToken(String token) {
        return parseToken(token, TOKEN_TYPE_REFRESH);
    }

    public long getAccessTokenValidity() {
        return jwtProperties.accessTokenValidity();
    }

    public long getRefreshTokenValidity() {
        return jwtProperties.refreshTokenValidity();
    }

    private String createToken(Admin admin, String tokenType, long validity) {
        if (admin.id() == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + validity);

        return Jwts.builder()
                .subject(String.valueOf(admin.id()))
                .claim(CLAIM_LOGIN_ID, admin.loginId())
                .claim(CLAIM_ROLE, admin.role().name())
                .claim(CLAIM_TYPE, tokenType)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(getSigningKey())
                .compact();
    }

    private TokenPayload parseToken(String token, String expectedTokenType) {
        Claims claims = parseClaims(token);

        try {
            Long adminId = Long.valueOf(claims.getSubject());
            String loginId = claims.get(CLAIM_LOGIN_ID, String.class);
            String role = claims.get(CLAIM_ROLE, String.class);
            String tokenType = claims.get(CLAIM_TYPE, String.class);

            if (loginId == null || role == null || tokenType == null) {
                throw new CustomException(ErrorCode.ADMIN_TOKEN_INVALID);
            }
            if (!expectedTokenType.equals(tokenType)) {
                throw new CustomException(ErrorCode.ADMIN_TOKEN_INVALID);
            }

            AdminRole adminRole = AdminRole.valueOf(role);
            return new TokenPayload(adminId, loginId, adminRole, tokenType);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.ADMIN_TOKEN_INVALID);
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.ADMIN_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.ADMIN_TOKEN_INVALID);
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public record TokenPayload(Long adminId, String loginId, AdminRole role, String tokenType) {
    }
}
