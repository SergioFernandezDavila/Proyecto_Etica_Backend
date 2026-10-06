package com.inmobiliaria.user_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UserContextFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(UserContextFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String userId = request.getHeader("X-Auth-User-Id");
    String rolesHeader = request.getHeader("X-Auth-Roles");

    // LOG PARA DEBUG
    logger.debug("X-Auth-User-Id: {}", userId);
    logger.debug("X-Auth-Roles raw: {}", rolesHeader);

    if (userId != null && rolesHeader != null) {
      String cleanRoles = rolesHeader.replace("[", "").replace("]", "").replace(" ", "");
      logger.debug("X-Auth-Roles cleaned: {}", cleanRoles);

      if (!cleanRoles.isEmpty()) {
        List<SimpleGrantedAuthority> authorities =
            Arrays.stream(cleanRoles.split(","))
                .filter(role -> !role.isEmpty())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()))
                .collect(Collectors.toList());

        logger.debug("Authorities created: {}", authorities);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    }
    filterChain.doFilter(request, response);
  }
}
