package com.inmobiliaria.property_service.repository;

import com.inmobiliaria.property_service.domain.PropertyDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyRepository extends MongoRepository<PropertyDocument, String> {
  List<PropertyDocument> findByDeletedFalse();

  List<PropertyDocument> findByAssignedAgentIdAndDeletedFalse(String assignedAgentId);

  List<PropertyDocument> findByOwnerIdAndDeletedFalse(String ownerId);

  List<PropertyDocument> findByAssignedAgentId(String assignedAgentId);

  List<PropertyDocument> findByOwnerId(String ownerId);
}
