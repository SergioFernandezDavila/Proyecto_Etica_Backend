package com.inmobiliaria.access_control_service.repository;

import com.inmobiliaria.access_control_service.domain.PermissionCatalogDocument;
import com.inmobiliaria.access_control_service.domain.ScopeType;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PermissionCatalogRepository
    extends MongoRepository<PermissionCatalogDocument, String> {

  Optional<PermissionCatalogDocument> findByResourceAndActionAndScope(
      String resource, String action, ScopeType scope);
}
