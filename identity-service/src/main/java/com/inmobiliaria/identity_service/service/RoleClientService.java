package com.inmobiliaria.identity_service.service;

import com.inmobiliaria.identity_service.client.RoleFeignClient;
import com.inmobiliaria.identity_service.dto.response.ExternalRoleResponse;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleClientService {

  private final RoleFeignClient roleFeignClient;

  public void validateRoleIdsExist(List<String> roleIds) {
    if (roleIds == null || roleIds.isEmpty()) {
      return;
    }

    Boolean isValid = roleFeignClient.validateRoleIds(roleIds);

    if (!Boolean.TRUE.equals(isValid)) {
      throw new IllegalArgumentException("One or more roleIds do not exist or are inactive");
    }
  }

  /**
   * Converts a list of role IDs (e.g. "rol_admin") into their corresponding role codes (e.g.
   * "ADMIN") by calling access-control-service.
   */
  public List<String> resolveRoleCodes(List<String> roleIds) {
    if (roleIds == null || roleIds.isEmpty()) {
      return List.of();
    }

    List<ExternalRoleResponse> roles = roleFeignClient.findByIds(roleIds);

    if (roles == null) {
      return List.of();
    }

    return roles.stream().map(ExternalRoleResponse::code).collect(Collectors.toList());
  }
}
