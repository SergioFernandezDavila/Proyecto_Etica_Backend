package com.inmobiliaria.access_control_service.service;

import com.inmobiliaria.access_control_service.domain.PermissionCatalogDocument;
import com.inmobiliaria.access_control_service.domain.PermissionEntry;
import com.inmobiliaria.access_control_service.dto.response.PermissionCatalogResponse;
import com.inmobiliaria.access_control_service.exception.ValidationException;
import com.inmobiliaria.access_control_service.repository.PermissionCatalogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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

  public void validatePermissionsExist(List<PermissionEntry> permissions) {
    for (PermissionEntry permission : permissions) {
      boolean exists =
          permissionCatalogRepository
              .findByResourceAndActionAndScope(
                  permission.getResource(), permission.getAction(), permission.getScope())
              .filter(PermissionCatalogDocument::getActive)
              .isPresent();

      if (!exists) {
        throw new ValidationException(
            "Permission not found in catalog: %s:%s:%s"
                .formatted(
                    permission.getResource(), permission.getAction(), permission.getScope()));
      }
    }
  }

  private PermissionCatalogResponse toResponse(PermissionCatalogDocument document) {
    return new PermissionCatalogResponse(
        document.getId(),
        document.getResource(),
        document.getAction(),
        document.getScope(),
        document.getDescription(),
        document.getActive());
  }
}
