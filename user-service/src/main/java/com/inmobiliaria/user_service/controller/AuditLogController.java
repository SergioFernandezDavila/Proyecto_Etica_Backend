package com.inmobiliaria.user_service.controller;

import com.inmobiliaria.user_service.domain.AuditLogDocument;
import com.inmobiliaria.user_service.dto.response.ApiResponse;
import com.inmobiliaria.user_service.dto.response.ResponseFactory;
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
@RequestMapping("/persons/audit")
@RequiredArgsConstructor
public class AuditLogController {

  private final MongoTemplate mongoTemplate;
  private final ResponseFactory responseFactory;

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<List<AuditLogDocument>>> getAuditLogs(
      @RequestParam(required = false) String personId,
      @RequestParam(required = false) String changedBy,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int pageSize) {

    Query query = new Query();
    if (personId != null && !personId.isBlank())
      query.addCriteria(Criteria.where("personId").is(personId));
    if (changedBy != null && !changedBy.isBlank())
      query.addCriteria(Criteria.where("changedBy").is(changedBy));
    if (action != null && !action.isBlank()) query.addCriteria(Criteria.where("action").is(action));

    if (from != null || to != null) {
      Criteria dateCriteria = Criteria.where("timestamp");
      if (from != null) dateCriteria.gte(from);
      if (to != null) dateCriteria.lte(to);
      query.addCriteria(dateCriteria);
    }

    long total = mongoTemplate.count(query, AuditLogDocument.class);
    query.with(PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "timestamp")));

    List<AuditLogDocument> logs = mongoTemplate.find(query, AuditLogDocument.class);
    Page<AuditLogDocument> logsPage = new PageImpl<>(logs, PageRequest.of(page, pageSize), total);

    return ResponseEntity.ok(responseFactory.paginated("Person audit logs retrieved", logsPage));
  }
}
