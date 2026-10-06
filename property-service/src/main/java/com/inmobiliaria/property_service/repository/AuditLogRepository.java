package com.inmobiliaria.property_service.repository;

import com.inmobiliaria.property_service.domain.AuditLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
  Page<AuditLog> findByPropertyIdOrderByTimestampDesc(String propertyId, Pageable pageable);

  List<AuditLog> findByPropertyIdOrderByTimestampDesc(String propertyId);
}
