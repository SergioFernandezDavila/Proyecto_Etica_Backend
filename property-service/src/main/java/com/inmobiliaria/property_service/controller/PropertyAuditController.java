package com.inmobiliaria.property_service.controller;

import com.inmobiliaria.property_service.domain.AuditLog;
import com.inmobiliaria.property_service.dto.response.ApiResponse;
import com.inmobiliaria.property_service.dto.response.ResponseFactory;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/properties/audit")
@RequiredArgsConstructor
public class PropertyAuditController {

  private final MongoTemplate mongoTemplate;
  private final ResponseFactory responseFactory;

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<List<AuditLog>>> getLogs(
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String propertyId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int pageSize) {

    Query query = new Query();
    if (userId != null && !userId.isBlank()) query.addCriteria(Criteria.where("userId").is(userId));
    if (propertyId != null && !propertyId.isBlank())
      query.addCriteria(Criteria.where("propertyId").is(propertyId));
    if (action != null && !action.isBlank()) query.addCriteria(Criteria.where("action").is(action));

    if (from != null || to != null) {
      Criteria dateCriteria = Criteria.where("timestamp");
      if (from != null) dateCriteria.gte(from);
      if (to != null) dateCriteria.lte(to);
      query.addCriteria(dateCriteria);
    }

    long total = mongoTemplate.count(query, AuditLog.class);
    query.with(PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "timestamp")));

    List<AuditLog> logs = mongoTemplate.find(query, AuditLog.class);
    Page<AuditLog> logsPage = new PageImpl<>(logs, PageRequest.of(page, pageSize), total);

    return ResponseEntity.ok(
        responseFactory.paginated("Property audit logs retrieved successfully", logsPage));
  }
}
