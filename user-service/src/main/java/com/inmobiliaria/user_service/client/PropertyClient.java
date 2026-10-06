package com.inmobiliaria.user_service.client;

import com.inmobiliaria.user_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "property-service", configuration = FeignConfig.class)
public interface PropertyClient {

  @GetMapping("/properties/{id}")
  PropertyResponse getProperty(@PathVariable("id") String id);

  record PropertyResponse(String id, String ownerId, String title) {}
}
