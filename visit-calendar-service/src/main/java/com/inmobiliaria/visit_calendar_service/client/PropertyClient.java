package com.inmobiliaria.visit_calendar_service.client;

import com.inmobiliaria.visit_calendar_service.config.FeignConfig;
import com.inmobiliaria.visit_calendar_service.dto.response.PropertyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "property-service", configuration = FeignConfig.class)
public interface PropertyClient {
  @GetMapping("/properties/{id}")
  PropertyResponse getPropertyById(@PathVariable("id") String id);

  // Método raw para debugging y solución temporal
  @GetMapping("/properties/{id}")
  String getPropertyRaw(@PathVariable("id") String id);
}
