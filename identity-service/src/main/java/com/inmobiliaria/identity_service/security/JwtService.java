package com.inmobiliaria.identity_service.security;

import com.inmobiliaria.identity_service.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final JwtProperties jwtProperties;
  private final SecretKey secretKey;

  public JwtService(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
  }

  public String generateAccessToken(UserPrincipal principal) {
    Instant now = Instant.now();
    Instant expiration = now.plusSeconds(jwtProperties.accessTokenExpirationSeconds());

    return Jwts.builder()
        .setSubject(principal.userId())
        .claim("email", principal.email())
        .claim("roles", principal.roleIds())
        .claim("userType", principal.userType())
        .claim("status", principal.status())
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(expiration))
        .signWith(secretKey)
        .compact();
  }

  public String generatePreAuthToken(String userId) {
    Instant now = Instant.now();
    Instant expiration = now.plusSeconds(300); // 5 minutes validity

    return Jwts.builder()
        .setSubject(userId)
        .claim("scope", "2FA_PRE_AUTH")
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(expiration))
        .signWith(secretKey)
        .compact();
  }

  public String validatePreAuthTokenAndGetUserId(String preAuthToken) {
    io.jsonwebtoken.Claims claims =
        Jwts.parserBuilder()
            .setSigningKey(secretKey)
            .build()
            .parseClaimsJws(preAuthToken)
            .getBody();

    if (!"2FA_PRE_AUTH".equals(claims.get("scope", String.class))) {
      throw new IllegalArgumentException("Invalid token scope for 2FA");
    }
    return claims.getSubject();
  }

  public long getAccessTokenExpirationSeconds() {
    return jwtProperties.accessTokenExpirationSeconds();
  }
}
