package com.inmobiliaria.api_gateway.util;

import com.inmobiliaria.api_gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  @Autowired private JwtProperties jwtProperties;

  private Key getSignKey() {
    return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
  }

  public void validateToken(final String token) {
    Jwts.parserBuilder().setSigningKey(getSignKey()).build().parseClaimsJws(token);
  }

  public Claims getClaims(final String token) {
    return Jwts.parserBuilder().setSigningKey(getSignKey()).build().parseClaimsJws(token).getBody();
  }
}
