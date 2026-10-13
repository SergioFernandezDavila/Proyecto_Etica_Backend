package com.inmobiliaria.access_control_service.service;

import com.inmobiliaria.access_control_service.domain.AccessType;
import com.inmobiliaria.access_control_service.domain.PermissionCatalogDocument;
import com.inmobiliaria.access_control_service.domain.PermissionEntry;
import com.inmobiliaria.access_control_service.domain.ScopeType;
import com.inmobiliaria.access_control_service.dto.response.PermissionCatalogResponse;
import com.inmobiliaria.access_control_service.exception.ResourceNotFoundException;
import com.inmobiliaria.access_control_service.repository.PermissionCatalogRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCatalogService {

  private final PermissionCatalogRepository permissionCatalogRepository;

  @Cacheable("permissionsCatalog")
  public List<PermissionCatalogResponse> findAll() {
    return permissionCatalogRepository.findAll().stream().map(this::toResponse).toList();
  }

  public Page<PermissionCatalogResponse> findAll(Pageable pageable) {
    return permissionCatalogRepository.findAll(pageable).map(this::toResponse);
  }

  @CacheEvict(value = "permissionsCatalog", allEntries = true)
  public PermissionCatalogResponse updateAccessType(String id, AccessType accessType) {
    PermissionCatalogDocument doc =
        permissionCatalogRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));

    doc.setAccessType(accessType);
    doc.setUpdatedAt(Instant.now());
    return toResponse(permissionCatalogRepository.save(doc));
  }

  public void validatePermissionsExist(List<PermissionEntry> permissions) {
    for (PermissionEntry permission : permissions) {
      Optional<PermissionCatalogDocument> found =
          permissionCatalogRepository.findByResourceAndActionAndScope(
              permission.getResource(), permission.getAction(), permission.getScope());

      if (found.isEmpty()) {
        PermissionCatalogDocument newPerm =
            PermissionCatalogDocument.builder()
                .resource(permission.getResource())
                .action(permission.getAction())
                .scope(permission.getScope())
                .description(
                    "Permiso para " + permission.getResource() + ":" + permission.getAction())
                .active(true)
                .accessType(
                    permission.getAccessType() != null
                        ? permission.getAccessType()
                        : AccessType.RBAC)
                .build();
        newPerm.setCreatedAt(Instant.now());
        newPerm.setUpdatedAt(Instant.now());
        newPerm.setCreatedBy("system");
        permissionCatalogRepository.save(newPerm);
      }
    }
  }

  @PostConstruct
  public void initStandardCatalog() {
    try {
      List<String> modules =
          List.of(
              "users",
              "properties",
              "visits",
              "operations",
              "contracts",
              "vehicles",
              "audit",
              "reports",
              "notifications",
              "auth");
      List<String> actions = List.of("create", "read", "update", "delete");

      for (String mod : modules) {
        for (String act : actions) {
          if (permissionCatalogRepository
              .findByResourceAndActionAndScope(mod, act, ScopeType.ANY)
              .isEmpty()) {
            AccessType defaultType =
                mod.equals("audit") || mod.equals("reports") ? AccessType.MAC : AccessType.RBAC;

            PermissionCatalogDocument doc =
                PermissionCatalogDocument.builder()
                    .resource(mod)
                    .action(act)
                    .scope(ScopeType.ANY)
                    .description("Permiso para " + mod + " (" + act + ")")
                    .active(true)
                    .accessType(defaultType)
                    .build();
            doc.setCreatedAt(Instant.now());
            doc.setUpdatedAt(Instant.now());
            doc.setCreatedBy("system");
            permissionCatalogRepository.save(doc);
          }
        }
      }
    } catch (Exception e) {
      log.warn("Catalog pre-seeding skipped: {}", e.getMessage());
    }
  }

  private PermissionCatalogResponse toResponse(PermissionCatalogDocument document) {
    return new PermissionCatalogResponse(
        document.getId(),
        document.getResource(),
        document.getAction(),
        document.getScope(),
        document.getDescription(),
        document.getActive(),
        document.getAccessType() != null ? document.getAccessType() : AccessType.RBAC);
  }
}
