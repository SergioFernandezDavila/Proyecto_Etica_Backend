package com.inmobiliaria.identity_service.client;

import com.inmobiliaria.identity_service.dto.response.ExternalRoleResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "access-control-service", path = "/roles")
public interface RoleFeignClient {

  @GetMapping("/list")
  List<ExternalRoleResponse> findByIds(@RequestParam("ids") List<String> ids);

  @GetMapping("/validate")
  Boolean validateRoleIds(@RequestParam("ids") List<String> ids);
}
