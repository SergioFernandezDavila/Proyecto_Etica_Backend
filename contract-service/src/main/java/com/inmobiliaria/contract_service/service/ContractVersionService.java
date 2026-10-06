package com.inmobiliaria.contract_service.service;

import com.inmobiliaria.contract_service.dto.ContractVersionResponse;
import com.inmobiliaria.contract_service.dto.CreateContractVersionRequest;
import com.inmobiliaria.contract_service.model.ContractVersion;
import com.inmobiliaria.contract_service.repository.ContractVersionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractVersionService {

  private final ContractVersionRepository contractVersionRepository;

  // ----------------------------------------------------------------
  // Crear nueva versión (solo ADMINISTRADOR)
  // ----------------------------------------------------------------
  public ContractVersionResponse createVersion(
      String operationId, CreateContractVersionRequest request, String userRole) {
    if (!"ADMIN".equalsIgnoreCase(userRole)) {
      throw new SecurityException("Solo los administradores pueden crear versiones de contrato");
    }

    // Calcular número de versión: busca la última y suma 1
    int nextVersionNumber =
        contractVersionRepository
            .findTopByOperationIdOrderByVersionNumberDesc(operationId)
            .map(v -> v.getVersionNumber() + 1)
            .orElse(1);

    ContractVersion version =
        ContractVersion.builder()
            .operationId(operationId)
            .versionNumber(nextVersionNumber)
            .content(request.getContent())
            .title(request.getTitle())
            .changeDescription(request.getChangeDescription())
            .authorId(request.getAuthorId())
            .authorName(request.getAuthorName())
            .authorRole(request.getAuthorRole())
            .createdAt(LocalDateTime.now())
            .status("ACTIVE")
            .build();

    ContractVersion saved = contractVersionRepository.save(version);
    log.info(
        "Nueva versión {} creada para operación {} por {}",
        nextVersionNumber,
        operationId,
        request.getAuthorName());

    return toResponse(saved);
  }

  // ----------------------------------------------------------------
  // Listar todas las versiones de un contrato
  // ----------------------------------------------------------------
  public List<ContractVersionResponse> getVersionsByOperation(String operationId) {
    List<ContractVersion> versions =
        contractVersionRepository.findByOperationIdOrderByVersionNumberAsc(operationId);

    log.info("Listando {} versiones para operación {}", versions.size(), operationId);
    return versions.stream().map(this::toResponse).collect(Collectors.toList());
  }

  // ----------------------------------------------------------------
  // Obtener una versión específica por ID
  // ----------------------------------------------------------------
  public ContractVersionResponse getVersionById(String versionId) {
    ContractVersion version =
        contractVersionRepository
            .findById(versionId)
            .orElseThrow(
                () ->
                    new RuntimeException("Versión de contrato no encontrada con ID: " + versionId));

    return toResponse(version);
  }

  // ----------------------------------------------------------------
  // Mapper model → response DTO
  // ----------------------------------------------------------------
  private ContractVersionResponse toResponse(ContractVersion v) {
    return ContractVersionResponse.builder()
        .id(v.getId())
        .operationId(v.getOperationId())
        .versionNumber(v.getVersionNumber())
        .content(v.getContent())
        .title(v.getTitle())
        .changeDescription(v.getChangeDescription())
        .authorId(v.getAuthorId())
        .authorName(v.getAuthorName())
        .authorRole(v.getAuthorRole())
        .createdAt(v.getCreatedAt())
        .status(v.getStatus())
        .build();
  }
}
