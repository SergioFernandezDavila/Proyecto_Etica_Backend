package com.inmobiliaria.visit_calendar_service.client;

import com.inmobiliaria.visit_calendar_service.dto.response.PersonResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", contextId = "personClient")
public interface PersonClient {

  @GetMapping("/persons/by-auth/{authUserId}")
  PersonResponse getPersonByAuthUserId(@PathVariable("authUserId") String authUserId);
}
