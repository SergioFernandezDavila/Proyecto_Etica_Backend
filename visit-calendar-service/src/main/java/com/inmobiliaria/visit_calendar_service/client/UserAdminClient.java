package com.inmobiliaria.visit_calendar_service.client;

import com.inmobiliaria.visit_calendar_service.config.FeignConfig;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "identity-service",
    contextId = "userAdminClient",
    configuration = FeignConfig.class)
public interface UserAdminClient {

  @GetMapping("/users")
  Map<String, Object> getUsers(
      @RequestParam("page") int page,
      @RequestParam("pageSize") int pageSize,
      @RequestParam(value = "role", required = false) String role);

  @GetMapping("/users/admins")
  Map<String, Object> getAdmins();
}
