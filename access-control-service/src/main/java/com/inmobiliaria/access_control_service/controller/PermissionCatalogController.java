package com.inmobiliaria.access_control_service.controller;

import com.inmobiliaria.access_control_service.dto.response.ApiResponse;
import com.inmobiliaria.access_control_service.dto.response.PermissionCatalogResponse;
import com.inmobiliaria.access_control_service.dto.response.ResponseFactory;
import com.inmobiliaria.access_control_service.service.PermissionCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionCatalogController {

  private final PermissionCatalogService permissionCatalogService;
  private final ResponseFactory responseFactory;

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<List<PermissionCatalogResponse>>> findAll(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int pageSize) {
    Page<PermissionCatalogResponse> permissionsPage =
        permissionCatalogService.findAll(PageRequest.of(page, pageSize));
    return ResponseEntity.ok(
        responseFactory.paginated("Permissions retrieved successfully", permissionsPage));
  }
}
