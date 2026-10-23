package com.inmobiliaria.access_control_service.controller;

import com.inmobiliaria.access_control_service.domain.AuditLog;
import com.inmobiliaria.access_control_service.dto.response.ApiResponse;
import com.inmobiliaria.access_control_service.dto.response.ResponseFactory;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/roles/audit")
@RequiredArgsConstructor
public class AuditController {

  private final MongoTemplate mongoTemplate;
  private final ResponseFactory responseFactory;

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs(
      @RequestParam(required = false) String roleId,
      @RequestParam(required = false) String roleCode,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int pageSize) {

    Query query = new Query();
    if (roleId != null && !roleId.isBlank()) {
      query.addCriteria(Criteria.where("roleId").is(roleId));
    }
    if (roleCode != null && !roleCode.isBlank()) {
      query.addCriteria(Criteria.where("roleCode").is(roleCode));
    }
    if (action != null && !action.isBlank()) {
      query.addCriteria(Criteria.where("action").is(action));
    }

    if (from != null || to != null) {
      Criteria dateCriteria = Criteria.where("timestamp");
      if (from != null) {
        dateCriteria.gte(from);
      }
      if (to != null) {
        dateCriteria.lte(to);
      }
      query.addCriteria(dateCriteria);
    }

    long total = mongoTemplate.count(query, AuditLog.class);
    query.with(PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "timestamp")));

    List<AuditLog> logs = mongoTemplate.find(query, AuditLog.class);
    Page<AuditLog> logsPage = new PageImpl<>(logs, PageRequest.of(page, pageSize), total);

    return ResponseEntity.ok(
        responseFactory.paginated("Access control audit logs retrieved", logsPage));
  }
}
