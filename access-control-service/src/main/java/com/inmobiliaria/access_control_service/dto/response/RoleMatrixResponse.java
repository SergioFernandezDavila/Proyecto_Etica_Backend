package com.inmobiliaria.access_control_service.dto.response;

import com.inmobiliaria.access_control_service.domain.AccessType;
import com.inmobiliaria.access_control_service.domain.RoleType;
import com.inmobiliaria.access_control_service.domain.ScopeType;
import java.util.List;
import java.util.Map;

public record RoleMatrixResponse(
    List<ModuleInfo> modules,
    List<String> availableActions,
    List<AccessTypeDefinition> accessTypes,
    List<RoleMatrixRowResponse> roles) {

  public record ModuleInfo(String code, String name, String description, String category) {}

  public record AccessTypeDefinition(
      AccessType code, String name, String theoreticalDefinition, String description) {}

  public record RoleMatrixModuleAccess(
      String resource, AccessType accessType, List<String> actions, ScopeType scope) {}

  public record RoleMatrixRowResponse(
      String roleId,
      String roleCode,
      String roleName,
      String roleDescription,
      RoleType roleType,
      Boolean active,
      Map<String, RoleMatrixModuleAccess> moduleAccess) {}
}
