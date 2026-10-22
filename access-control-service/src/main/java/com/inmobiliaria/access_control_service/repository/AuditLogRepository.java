package com.inmobiliaria.access_control_service.repository;

import com.inmobiliaria.access_control_service.domain.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {}
