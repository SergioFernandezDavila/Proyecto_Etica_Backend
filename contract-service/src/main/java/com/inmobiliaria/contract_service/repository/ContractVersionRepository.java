package com.inmobiliaria.contract_service.repository;

import com.inmobiliaria.contract_service.model.ContractVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractVersionRepository extends MongoRepository<ContractVersion, String> {

  /** Obtiene todas las versiones de un contrato ordenadas por número de versión ascendente */
  List<ContractVersion> findByOperationIdOrderByVersionNumberAsc(String operationId);

  /** Obtiene la última versión de un contrato (mayor número de versión) */
  Optional<ContractVersion> findTopByOperationIdOrderByVersionNumberDesc(String operationId);

  /** Verifica si ya existe al menos una versión para una operación */
  boolean existsByOperationId(String operationId);

  /** Cuenta cuántas versiones tiene un contrato */
  long countByOperationId(String operationId);
}
