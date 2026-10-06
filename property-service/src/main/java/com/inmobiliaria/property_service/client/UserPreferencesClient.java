package com.inmobiliaria.property_service.client;

import com.inmobiliaria.property_service.config.FeignConfig;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "user-service",
    contextId = "userPreferencesClient",
    configuration = FeignConfig.class)
public interface UserPreferencesClient {

  @GetMapping("/persons/{personId}/preferencias")
  UserPreferenceResponse getPreferences(@PathVariable("personId") String personId);

  record UserPreferenceResponse(
      List<String> preferredZones,
      Integer minRooms,
      Integer maxRooms,
      Double maxPrice,
      String preferredPropertyType) {}
}
