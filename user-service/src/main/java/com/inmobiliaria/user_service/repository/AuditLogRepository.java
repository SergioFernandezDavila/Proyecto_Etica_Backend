package com.inmobiliaria.user_service.repository;

import com.inmobiliaria.user_service.domain.AuditLogDocument;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditLogDocument, String> {
  List<AuditLogDocument> findAll(Sort sort);
}
