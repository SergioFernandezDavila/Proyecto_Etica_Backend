// backend/visit-calendar-service/src/main/java/.../repository/AlertConfigRepository.java
package com.inmobiliaria.visit_calendar_service.repository;

import com.inmobiliaria.visit_calendar_service.model.AlertConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AlertConfigRepository extends MongoRepository<AlertConfig, String> {}
