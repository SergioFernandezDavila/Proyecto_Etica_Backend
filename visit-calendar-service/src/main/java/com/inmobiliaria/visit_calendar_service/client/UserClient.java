package com.inmobiliaria.visit_calendar_service.client;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service")
public interface UserClient {

  @GetMapping("/users/{id}")
  Map<String, Object> getUserById(@PathVariable("id") String id);
}
