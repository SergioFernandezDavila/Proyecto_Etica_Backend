package com.inmobiliaria.access_control_service.security;

import com.inmobiliaria.access_control_service.domain.AuditLog;
import com.inmobiliaria.access_control_service.dto.request.RoleMatrixBatchUpdateRequest;
import com.inmobiliaria.access_control_service.dto.response.RoleResponse;
import com.inmobiliaria.access_control_service.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

  private final AuditLogRepository auditLogRepository;

  @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
  public void auditAction(JoinPoint joinPoint, Auditable auditable, Object result) {
    try {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      String performedById =
          (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : "SYSTEM";

      HttpServletRequest request = getHttpRequest();
      String performedByEmail = request != null ? request.getHeader("X-Auth-User-Email") : null;
      if (performedByEmail == null || performedByEmail.isBlank()) {
        performedByEmail = performedById;
      }
      String performedByName =
          request != null ? request.getHeader("X-Auth-User-Name") : performedById;

      String roleId = "N/A";
      String roleCode = "N/A";

      if (result instanceof RoleResponse roleResp) {
        roleId = roleResp.id();
        roleCode = roleResp.code();
      } else if (joinPoint.getArgs().length > 0) {
        Object firstArg = joinPoint.getArgs()[0];
        if (firstArg instanceof String idStr) {
          roleId = idStr;
        } else if (firstArg instanceof RoleMatrixBatchUpdateRequest batchReq) {
          roleId =
              batchReq.updates().stream()
                  .map(RoleMatrixBatchUpdateRequest.RoleMatrixRowUpdate::roleId)
                  .collect(Collectors.joining(", "));
          roleCode = "BATCH_UPDATE";
        }
      }

      String ipAddress = getClientIp();
      String details = buildDetails(joinPoint, auditable, result);

      AuditLog auditLog =
          AuditLog.builder()
              .action(auditable.action())
              .roleId(roleId)
              .roleCode(roleCode)
              .performedBy(performedById)
              .performedByEmail(performedByEmail)
              .performedByName(performedByName)
              .timestamp(Instant.now())
              .details(details)
              .ipAddress(ipAddress)
              .build();

      auditLogRepository.save(auditLog);
      log.debug("Audit logged for action: {} by {}", auditable.action(), performedByEmail);

    } catch (Exception e) {
      log.error("Failed to generate audit log: {}", e.getMessage(), e);
    }
  }

  private String buildDetails(JoinPoint joinPoint, Auditable auditable, Object result) {
    StringBuilder details = new StringBuilder();
    details.append(auditable.description());

    if (result instanceof RoleResponse roleResp) {
      if (!details.isEmpty() && !details.toString().endsWith(".")) {
        details.append(". ");
      }
      details
          .append("Rol: ")
          .append(roleResp.name())
          .append(" (")
          .append(roleResp.code())
          .append(")");
      if (roleResp.permissions() != null) {
        details.append(" - Cantidad de permisos: ").append(roleResp.permissions().size());
      }
    } else if (joinPoint.getArgs().length > 0
        && joinPoint.getArgs()[0] instanceof RoleMatrixBatchUpdateRequest batchReq) {
      if (!details.isEmpty() && !details.toString().endsWith(".")) {
        details.append(". ");
      }
      details.append("Roles actualizados en matriz: ").append(batchReq.updates().size());
    }

    return details.toString();
  }

  private HttpServletRequest getHttpRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
  }

  private String getClientIp() {
    try {
      HttpServletRequest request = getHttpRequest();
      if (request != null) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
          ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
          ip = request.getRemoteAddr();
        }
        return ip;
      }
    } catch (Exception e) {
      log.debug("Could not determine client IP: {}", e.getMessage());
    }
    return "UNKNOWN";
  }
}
