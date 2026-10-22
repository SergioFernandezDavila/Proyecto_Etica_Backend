package com.inmobiliaria.access_control_service.service;

import com.inmobiliaria.access_control_service.domain.AccessType;
import com.inmobiliaria.access_control_service.domain.PermissionEntry;
import com.inmobiliaria.access_control_service.domain.RoleDocument;
import com.inmobiliaria.access_control_service.domain.RoleType;
import com.inmobiliaria.access_control_service.domain.ScopeType;
import com.inmobiliaria.access_control_service.dto.request.CreateRoleRequest;
import com.inmobiliaria.access_control_service.dto.request.PermissionRequest;
import com.inmobiliaria.access_control_service.dto.request.RoleMatrixBatchUpdateRequest;
import com.inmobiliaria.access_control_service.dto.request.UpdateRoleRequest;
import com.inmobiliaria.access_control_service.dto.response.RoleMatrixResponse;
import com.inmobiliaria.access_control_service.dto.response.RoleResponse;
import com.inmobiliaria.access_control_service.exception.ResourceAlreadyExistsException;
import com.inmobiliaria.access_control_service.exception.ResourceNotFoundException;
import com.inmobiliaria.access_control_service.exception.ValidationException;
import com.inmobiliaria.access_control_service.repository.RoleRepository;
import com.inmobiliaria.access_control_service.security.Auditable;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class RoleService {

  private final RoleRepository roleRepository;
  private final PermissionCatalogService permissionCatalogService;

  @Cacheable("roles")
  public Page<RoleResponse> findAll(Pageable pageable) {
    return roleRepository.findAll(pageable).map(this::toResponse);
  }

  public RoleResponse findById(String id) {
    return toResponse(findDocumentById(id));
  }

  public List<RoleResponse> findByIds(List<String> ids) {
    return roleRepository.findByIdInAndActiveTrue(ids).stream().map(this::toResponse).toList();
  }

  public List<PermissionEntry> findPermissionsByRoleCodes(String rolesHeader) {
    List<PermissionEntry> result = new ArrayList<>();
    if (rolesHeader == null || rolesHeader.isBlank()) {
      return result;
    }
    String cleaned = rolesHeader.replace("[", "").replace("]", "");
    for (String code : cleaned.split(",")) {
      String trimmed = code.trim().toUpperCase();
      if (trimmed.isEmpty()) {
        continue;
      }
      roleRepository
          .findByCode(trimmed)
          .filter(role -> Boolean.TRUE.equals(role.getActive()))
          .ifPresent(
              role -> {
                if (role.getPermissions() != null) {
                  result.addAll(role.getPermissions());
                }
              });
    }
    return result;
  }

  @Auditable(action = "ROLE_CREATE", description = "Creación de nuevo rol en el sistema")
  @CacheEvict(
      value = {"roles", "rolesMatrix"},
      allEntries = true)
  public RoleResponse create(CreateRoleRequest request) {
    String normalizedCode = request.code().trim().toUpperCase();
    if (roleRepository.existsByCode(normalizedCode)) {
      throw new ResourceAlreadyExistsException("Role code already exists: " + normalizedCode);
    }

    if (roleRepository.existsByName(request.name().trim())) {
      throw new ResourceAlreadyExistsException("Role name already exists: " + request.name());
    }

    List<PermissionEntry> permissions = mapPermissions(request.permissions());
    permissionCatalogService.validatePermissionsExist(permissions);

    RoleDocument document =
        RoleDocument.builder()
            .code(normalizedCode)
            .name(request.name().trim())
            .description(request.description().trim())
            .type(RoleType.CUSTOM)
            .active(true)
            .permissions(permissions)
            .version(1)
            .build();

    document.setCreatedAt(Instant.now());
    document.setUpdatedAt(Instant.now());
    document.setCreatedBy("system");

    return toResponse(roleRepository.save(document));
  }

  @Auditable(
      action = "ROLE_UPDATE",
      description = "Actualización de información y permisos del rol")
  @CacheEvict(
      value = {"roles", "rolesMatrix"},
      allEntries = true)
  public RoleResponse update(String id, UpdateRoleRequest request) {
    RoleDocument existing = findDocumentById(id);

    List<PermissionEntry> permissions = mapPermissions(request.permissions());
    permissionCatalogService.validatePermissionsExist(permissions);

    existing.setName(request.name().trim());
    existing.setDescription(request.description().trim());
    existing.setPermissions(permissions);
    existing.setActive(request.active() != null ? request.active() : existing.getActive());
    existing.setVersion(existing.getVersion() != null ? existing.getVersion() + 1 : 1);
    existing.setUpdatedAt(Instant.now());

    return toResponse(roleRepository.save(existing));
  }

  @Auditable(action = "ROLE_DELETE", description = "Eliminación de rol personalizado del sistema")
  @CacheEvict(
      value = {"roles", "rolesMatrix"},
      allEntries = true)
  public void delete(String id) {
    RoleDocument existing = findDocumentById(id);

    if (existing.getType() == RoleType.SYSTEM) {
      throw new ValidationException(
          "No se pueden eliminar los roles reservados del sistema: " + existing.getCode());
    }

    roleRepository.delete(existing);
    log.info("Rol con ID {} ({}) eliminado exitosamente", id, existing.getCode());
  }

  @Cacheable("rolesMatrix")
  public RoleMatrixResponse getMatrix() {
    List<RoleMatrixResponse.ModuleInfo> modules =
        List.of(
            new RoleMatrixResponse.ModuleInfo(
                "users",
                "Agentes y Usuarios",
                "Gestión de perfiles, agentes inmobiliarios, propietarios y clientes",
                "Administración"),
            new RoleMatrixResponse.ModuleInfo(
                "properties",
                "Propiedades e Inventario",
                "Fichas técnicas, inventario, precios y estados de inmuebles",
                "Inmobiliaria"),
            new RoleMatrixResponse.ModuleInfo(
                "visits",
                "Calendario y Visitas",
                "Agenda, asignación y seguimiento de visitas a inmuebles",
                "Operaciones"),
            new RoleMatrixResponse.ModuleInfo(
                "operations",
                "Operaciones y Negocios",
                "Transacciones comerciales de venta, arriendo y reservas",
                "Operaciones"),
            new RoleMatrixResponse.ModuleInfo(
                "contracts",
                "Contratos Legales",
                "Generación de borradores, revisión y control de versiones",
                "Legal"),
            new RoleMatrixResponse.ModuleInfo(
                "vehicles",
                "Gestión Vehicular",
                "Flota de transporte para visitas y control de kilometraje",
                "Logística"),
            new RoleMatrixResponse.ModuleInfo(
                "audit",
                "Auditoría del Sistema",
                "Pistas de auditoría, registro inmutable de acciones y seguridad",
                "Gobernanza"),
            new RoleMatrixResponse.ModuleInfo(
                "reports",
                "Reportes y Estadísticas",
                "Métricas gerenciales, rendimiento de agentes y conversión",
                "Inteligencia"),
            new RoleMatrixResponse.ModuleInfo(
                "notifications",
                "Notificaciones y Alertas",
                "Alertas en tiempo real y recordatorios de visitas",
                "Comunicación"));

    List<String> availableActions = List.of("create", "read", "update", "delete");

    List<RoleMatrixResponse.AccessTypeDefinition> accessTypes =
        List.of(
            new RoleMatrixResponse.AccessTypeDefinition(
                AccessType.MAC,
                "MAC (Mandatory Access Control)",
                "Función / Área general",
                "Acceso obligatorio determinado por la política central y el área funcional u organigrama general de la organización."),
            new RoleMatrixResponse.AccessTypeDefinition(
                AccessType.RBAC,
                "RBAC (Role-Based Access Control)",
                "Segregación / Restricción de funciones",
                "Acceso gobernado estrictamente por el rol asignado, asegurando segregación de funciones operativas y responsabilidades."),
            new RoleMatrixResponse.AccessTypeDefinition(
                AccessType.DAC,
                "DAC (Discretionary Access Control)",
                "Temporal / Puntual",
                "Acceso discrecional conferido para una necesidad temporal o puntual, por delegación directa de facultades."));

    List<RoleDocument> allRoles = roleRepository.findAll();
    List<RoleMatrixResponse.RoleMatrixRowResponse> rows = new ArrayList<>();

    for (RoleDocument role : allRoles) {
      Map<String, RoleMatrixResponse.RoleMatrixModuleAccess> moduleAccessMap = new HashMap<>();

      if (role.getPermissions() != null) {
        Map<String, List<PermissionEntry>> groupedByResource = new HashMap<>();
        for (PermissionEntry entry : role.getPermissions()) {
          groupedByResource.computeIfAbsent(entry.getResource(), k -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<String, List<PermissionEntry>> entry : groupedByResource.entrySet()) {
          String resource = entry.getKey();
          List<PermissionEntry> entries = entry.getValue();

          List<String> actions =
              entries.stream().map(PermissionEntry::getAction).distinct().toList();

          AccessType accessType =
              entries.stream()
                  .map(PermissionEntry::getAccessType)
                  .filter(at -> at != null)
                  .findFirst()
                  .orElseGet(
                      () ->
                          "ADMIN".equalsIgnoreCase(role.getCode())
                              ? AccessType.MAC
                              : AccessType.RBAC);

          ScopeType scope =
              entries.stream()
                  .map(PermissionEntry::getScope)
                  .filter(s -> s != null)
                  .findFirst()
                  .orElse(ScopeType.ANY);

          moduleAccessMap.put(
              resource,
              new RoleMatrixResponse.RoleMatrixModuleAccess(resource, accessType, actions, scope));
        }
      }

      rows.add(
          new RoleMatrixResponse.RoleMatrixRowResponse(
              role.getId(),
              role.getCode(),
              role.getName(),
              role.getDescription(),
              role.getType(),
              role.getActive(),
              moduleAccessMap));
    }

    return new RoleMatrixResponse(modules, availableActions, accessTypes, rows);
  }

  @Auditable(
      action = "ROLE_MATRIX_UPDATE",
      description = "Actualización masiva de matriz de roles y permisos")
  @CacheEvict(
      value = {"roles", "rolesMatrix"},
      allEntries = true)
  public RoleMatrixResponse updateMatrix(RoleMatrixBatchUpdateRequest request) {
    for (RoleMatrixBatchUpdateRequest.RoleMatrixRowUpdate rowUpdate : request.updates()) {
      RoleDocument role = findDocumentById(rowUpdate.roleId());

      List<PermissionEntry> updatedPermissions = new ArrayList<>();
      Map<String, Boolean> handledModules = new HashMap<>();

      for (RoleMatrixBatchUpdateRequest.RoleModuleAccessUpdate moduleUpdate : rowUpdate.modules()) {
        handledModules.put(moduleUpdate.resource(), true);
        AccessType accessType =
            moduleUpdate.accessType() != null
                ? moduleUpdate.accessType()
                : ("ADMIN".equalsIgnoreCase(role.getCode()) ? AccessType.MAC : AccessType.RBAC);

        if (moduleUpdate.actions() != null) {
          for (String action : moduleUpdate.actions()) {
            if (action != null && !action.isBlank()) {
              updatedPermissions.add(
                  PermissionEntry.builder()
                      .resource(moduleUpdate.resource().trim())
                      .action(action.trim())
                      .scope(ScopeType.ANY)
                      .accessType(accessType)
                      .build());
            }
          }
        }
      }

      // Preserve any permissions outside the modules touched in this update
      if (role.getPermissions() != null) {
        for (PermissionEntry existingEntry : role.getPermissions()) {
          if (!handledModules.containsKey(existingEntry.getResource())) {
            updatedPermissions.add(existingEntry);
          }
        }
      }

      permissionCatalogService.validatePermissionsExist(updatedPermissions);

      role.setPermissions(updatedPermissions);
      role.setVersion(role.getVersion() != null ? role.getVersion() + 1 : 1);
      role.setUpdatedAt(Instant.now());
      roleRepository.save(role);
    }

    return getMatrix();
  }

  private RoleDocument findDocumentById(String id) {
    return roleRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
  }

  private List<PermissionEntry> mapPermissions(List<PermissionRequest> permissions) {
    if (permissions == null) {
      return List.of();
    }
    return permissions.stream()
        .map(
            permission ->
                PermissionEntry.builder()
                    .resource(permission.resource().trim())
                    .action(permission.action().trim())
                    .scope(permission.scope())
                    .accessType(
                        permission.accessType() != null ? permission.accessType() : AccessType.RBAC)
                    .build())
        .toList();
  }

  private RoleResponse toResponse(RoleDocument document) {
    return new RoleResponse(
        document.getId(),
        document.getCode(),
        document.getName(),
        document.getDescription(),
        document.getType(),
        document.getActive(),
        document.getPermissions(),
        document.getVersion());
  }

  @PostConstruct
  public void initDefaultRoles() {
    try {
      if (roleRepository.count() == 0) {
        log.info("Seeding initial default roles with theoretical access types...");

        List<String> allModules =
            List.of(
                "users",
                "properties",
                "visits",
                "operations",
                "contracts",
                "vehicles",
                "audit",
                "reports",
                "notifications");
        List<String> crudActions = List.of("create", "read", "update", "delete");

        // 1. ADMIN (MAC: Función/Área General)
        List<PermissionEntry> adminPerms = new ArrayList<>();
        for (String mod : allModules) {
          for (String act : crudActions) {
            adminPerms.add(
                PermissionEntry.builder()
                    .resource(mod)
                    .action(act)
                    .scope(ScopeType.ANY)
                    .accessType(AccessType.MAC)
                    .build());
          }
        }
        RoleDocument admin =
            RoleDocument.builder()
                .id("rol_admin")
                .code("ADMIN")
                .name("Administrador General")
                .description("Acceso de gobierno general del sistema inmobiliario (MAC)")
                .type(RoleType.SYSTEM)
                .active(true)
                .permissions(adminPerms)
                .version(1)
                .build();
        admin.setCreatedAt(Instant.now());
        admin.setUpdatedAt(Instant.now());
        admin.setCreatedBy("system");
        roleRepository.save(admin);

        // 2. AGENT (RBAC: Segregación/Restricción de funciones)
        List<PermissionEntry> agentPerms = new ArrayList<>();
        List<String> agentModules = List.of("properties", "visits", "operations", "users");
        for (String mod : agentModules) {
          for (String act : List.of("read", "create", "update")) {
            agentPerms.add(
                PermissionEntry.builder()
                    .resource(mod)
                    .action(act)
                    .scope(ScopeType.ANY)
                    .accessType(AccessType.RBAC)
                    .build());
          }
        }
        RoleDocument agent =
            RoleDocument.builder()
                .id("rol_agent")
                .code("AGENT")
                .name("Agente Inmobiliario")
                .description("Operación comercial y visitas de inmuebles (RBAC)")
                .type(RoleType.SYSTEM)
                .active(true)
                .permissions(agentPerms)
                .version(1)
                .build();
        agent.setCreatedAt(Instant.now());
        agent.setUpdatedAt(Instant.now());
        agent.setCreatedBy("system");
        roleRepository.save(agent);

        // 3. OWNER (RBAC: Propietario de Inmuebles)
        List<PermissionEntry> ownerPerms = new ArrayList<>();
        for (String act : List.of("read", "update")) {
          ownerPerms.add(
              PermissionEntry.builder()
                  .resource("properties")
                  .action(act)
                  .scope(ScopeType.OWN)
                  .accessType(AccessType.RBAC)
                  .build());
        }
        ownerPerms.add(
            PermissionEntry.builder()
                .resource("operations")
                .action("read")
                .scope(ScopeType.OWN)
                .accessType(AccessType.RBAC)
                .build());
        RoleDocument owner =
            RoleDocument.builder()
                .id("rol_owner")
                .code("OWNER")
                .name("Propietario")
                .description("Consulta de sus propiedades y liquidaciones (RBAC)")
                .type(RoleType.SYSTEM)
                .active(true)
                .permissions(ownerPerms)
                .version(1)
                .build();
        owner.setCreatedAt(Instant.now());
        owner.setUpdatedAt(Instant.now());
        owner.setCreatedBy("system");
        roleRepository.save(owner);

        // 4. CLIENT (RBAC: Cliente Interesado)
        List<PermissionEntry> clientPerms = new ArrayList<>();
        clientPerms.add(
            PermissionEntry.builder()
                .resource("properties")
                .action("read")
                .scope(ScopeType.ANY)
                .accessType(AccessType.RBAC)
                .build());
        clientPerms.add(
            PermissionEntry.builder()
                .resource("visits")
                .action("create")
                .scope(ScopeType.OWN)
                .accessType(AccessType.RBAC)
                .build());
        RoleDocument client =
            RoleDocument.builder()
                .id("rol_interested_client")
                .code("INTERESTED_CLIENT")
                .name("Cliente Interesado")
                .description("Búsqueda de inmuebles y solicitud de visitas (RBAC)")
                .type(RoleType.SYSTEM)
                .active(true)
                .permissions(clientPerms)
                .version(1)
                .build();
        client.setCreatedAt(Instant.now());
        client.setUpdatedAt(Instant.now());
        client.setCreatedBy("system");
        roleRepository.save(client);

        log.info("Default roles seeded successfully.");
      }
    } catch (Exception e) {
      log.warn("Default role seeding skipped: {}", e.getMessage());
    }
  }
}
