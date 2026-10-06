package com.inmobiliaria.notification_service.config;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

public class HasServiceNameAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {
  @Override
  public AuthorizationDecision check(
      Supplier<Authentication> authentication, RequestAuthorizationContext context) {
    String serviceName = context.getRequest().getHeader("X-Service-Name");
    boolean authorized = serviceName != null && !serviceName.isBlank();
    return new AuthorizationDecision(authorized);
  }
}
