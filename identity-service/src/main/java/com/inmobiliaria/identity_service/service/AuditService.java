package com.inmobiliaria.identity_service.service;

import com.inmobiliaria.identity_service.domain.AuditLog;
import com.inmobiliaria.identity_service.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

  private final AuditLogRepository auditLogRepository;

  public void record(AuditLog auditLog) {
    try {
      if (auditLog.getTimestamp() == null) {
        auditLog.setTimestamp(Instant.now());
      }

      if (auditLog.getStatus() == null) {
        auditLog.setStatus("SUCCESS");
      }

      if (auditLog.getPerformedBy() == null) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
          String principal = auth.getPrincipal().toString();
          if (!"anonymousUser".equalsIgnoreCase(principal)) {
            auditLog.setPerformedBy(principal);
          }
        }
      }

      if (auditLog.getIpAddress() == null) {
        String ip = extractClientIp();
        auditLog.setIpAddress(ip);
      }

      auditLogRepository.save(auditLog);
    } catch (Exception e) {
      log.error("Failed to persist audit log: {}", e.getMessage(), e);
    }
  }

  private String extractClientIp() {
    try {
      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs != null) {
        HttpServletRequest req = attrs.getRequest();
        String xForwardedFor = req.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
          return xForwardedFor.split(",")[0].trim();
        }
        return req.getRemoteAddr();
      }
    } catch (Exception ignored) {
    }
    return "UNKNOWN";
  }
}
