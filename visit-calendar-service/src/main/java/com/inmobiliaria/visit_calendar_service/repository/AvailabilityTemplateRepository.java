package com.inmobiliaria.visit_calendar_service.repository;

import com.inmobiliaria.visit_calendar_service.model.AvailabilityTemplate;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvailabilityTemplateRepository
    extends MongoRepository<AvailabilityTemplate, String> {

  Optional<AvailabilityTemplate> findByIsStandard(boolean isStandard);
}
