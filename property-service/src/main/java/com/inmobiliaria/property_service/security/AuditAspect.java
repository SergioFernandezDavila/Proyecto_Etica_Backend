package com.inmobiliaria.property_service.security;

import com.inmobiliaria.property_service.domain.AuditLog;
import com.inmobiliaria.property_service.domain.PropertyDocument;
import com.inmobiliaria.property_service.dto.response.PropertyResponse;
import com.inmobiliaria.property_service.repository.AuditLogRepository;
import com.inmobiliaria.property_service.repository.PropertyRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

  private final AuditLogRepository auditLogRepository;
  private final PropertyRepository propertyRepository;

  @Around("@annotation(auditable)")
  public Object auditPropertyChange(ProceedingJoinPoint joinPoint, Auditable auditable)
      throws Throwable {
    Object[] args = joinPoint.getArgs();
    String action = auditable.action();
    String propertyId = null;

    PropertyDocument beforeState = null;

    // 1. Capture state BEFORE
    if (!action.equals("PROPERTY_CREATE") && args.length > 0 && args[0] instanceof String) {
      propertyId = (String) args[0];
      beforeState = propertyRepository.findById(propertyId).orElse(null);
    }

    // 2. Execute the actual update
    Object result = joinPoint.proceed();

    // 3. Capture state AFTER and compare
    List<AuditLog.FieldChange> changes = new ArrayList<>();
    String previousSummary = "N/A";
    String newSummary = "N/A";

    if (action.equals("PROPERTY_CREATE") && result instanceof PropertyResponse res) {
      propertyId = res.id();
      previousSummary = "NEW_PROPERTY";
      newSummary = res.title();
    } else if (beforeState != null) {
      PropertyDocument afterState = propertyRepository.findById(propertyId).orElse(null);
      if (afterState != null) {
        changes = calculateChanges(beforeState, afterState);

        if (changes.isEmpty()) {
          return result;
        }

        // Summaries for the main table view
        previousSummary = beforeState.getStatus() != null ? beforeState.getStatus().name() : "N/A";
        newSummary = afterState.getStatus() != null ? afterState.getStatus().name() : "N/A";

        if (action.equals("PROPERTY_UPDATE")) {
          previousSummary = beforeState.getTitle();
          newSummary = afterState.getTitle();
        }
      }
    }

    // 4. Save the log with details
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String userId = (auth != null) ? auth.getName() : "SYSTEM_UNKNOWN";
    AuditLog log =
        AuditLog.builder()
            .userId(userId)
            .action(action)
            .propertyId(propertyId)
            .previousValue(previousSummary)
            .newValue(newSummary)
            .changes(changes) // Detailed list
            .timestamp(Instant.now())
            .build();

    auditLogRepository.save(log);
    return result;
  }

  private List<AuditLog.FieldChange> calculateChanges(
      PropertyDocument oldDoc, PropertyDocument newDoc) {
    List<AuditLog.FieldChange> changes = new ArrayList<>();

    compare(changes, "title", oldDoc.getTitle(), newDoc.getTitle());
    compare(changes, "address", oldDoc.getAddress(), newDoc.getAddress());
    compare(changes, "price", oldDoc.getPrice(), newDoc.getPrice());
    compare(changes, "type", oldDoc.getType(), newDoc.getType());
    compare(changes, "m2", oldDoc.getM2(), newDoc.getM2());
    compare(changes, "rooms", oldDoc.getRooms(), newDoc.getRooms());
    compare(changes, "status", oldDoc.getStatus(), newDoc.getStatus());
    compare(changes, "operationType", oldDoc.getOperationType(), newDoc.getOperationType());
    compare(changes, "ownerId", oldDoc.getOwnerId(), newDoc.getOwnerId());
    compare(changes, "agentId", oldDoc.getAssignedAgentId(), newDoc.getAssignedAgentId());
    compare(changes, "latitude", oldDoc.getLatitude(), newDoc.getLatitude());
    compare(changes, "longitude", oldDoc.getLongitude(), newDoc.getLongitude());

    return changes;
  }

  private void compare(
      List<AuditLog.FieldChange> list, String field, Object oldVal, Object newVal) {
    if (!Objects.equals(oldVal, newVal)) {
      list.add(new AuditLog.FieldChange(field, String.valueOf(oldVal), String.valueOf(newVal)));
    }
  }
}
