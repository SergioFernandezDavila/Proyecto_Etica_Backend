package com.inmobiliaria.property_service.controller;

import com.inmobiliaria.property_service.domain.AuditLog;
import com.inmobiliaria.property_service.dto.response.ApiResponse;
import com.inmobiliaria.property_service.dto.response.ResponseFactory;
import com.inmobiliaria.property_service.repository.AuditLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/properties/audit")
@RequiredArgsConstructor
public class AuditController {

  private final AuditLogRepository auditLogRepository;
  private final ResponseFactory responseFactory;

  @GetMapping("/{resourceId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs(
      @PathVariable String resourceId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    Page<AuditLog> logsPage =
        auditLogRepository.findByPropertyIdOrderByTimestampDesc(
            resourceId, PageRequest.of(page, pageSize));
    return ResponseEntity.ok(
        responseFactory.paginated("Audit logs retrieved successfully", logsPage));
  }
}
