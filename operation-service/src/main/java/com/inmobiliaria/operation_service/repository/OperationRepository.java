package com.inmobiliaria.operation_service.repository;

import com.inmobiliaria.operation_service.domain.OperationDocument;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OperationRepository extends MongoRepository<OperationDocument, String> {
  List<OperationDocument> findByStatusAndClosureDateBetween(
      String status, LocalDateTime start, LocalDateTime end);

  List<OperationDocument> findByAgentId(String agentId);

  List<OperationDocument> findByOwnerId(String ownerId);

  List<OperationDocument> findByClientId(String clientId);

  java.util.Optional<OperationDocument> findByPropertyIdAndStatus(String propertyId, String status);

  java.util.Optional<OperationDocument> findFirstByPropertyIdAndStatusOrderByCreatedAtDesc(
      String propertyId, String status);

  java.util.Optional<OperationDocument> findFirstByPropertyIdOrderByCreatedAtDesc(
      String propertyId);

  long countByStatusIn(List<String> statuses);
}
