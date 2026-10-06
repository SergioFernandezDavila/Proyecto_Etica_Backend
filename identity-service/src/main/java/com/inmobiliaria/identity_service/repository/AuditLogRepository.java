package com.inmobiliaria.identity_service.repository;

import com.inmobiliaria.identity_service.domain.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {}
