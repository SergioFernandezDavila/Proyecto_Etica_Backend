// backend/notification-service/src/main/java/.../config/SecurityConfig.java
package com.inmobiliaria.notification_service.config;

import com.inmobiliaria.notification_service.security.UserContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, UserContextFilter userContextFilter) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth
                    // Solo POST a /notifications/in-app requiere X-Service-Name (llamadas internas)
                    .requestMatchers(HttpMethod.POST, "/notifications/in-app")
                    .access(new HasServiceNameAuthorizationManager())
                    // GET, PUT, etc. para consultar y marcar como leídas: permitidas (el
                    // UserContextFilter ya pondrá el usuario)
                    .requestMatchers("/notifications/in-app/**")
                    .permitAll()
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(userContextFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
