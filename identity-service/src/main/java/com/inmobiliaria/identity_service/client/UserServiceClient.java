package com.inmobiliaria.identity_service.client;

import com.inmobiliaria.identity_service.client.dto.CreatePersonRequest;
import com.inmobiliaria.identity_service.client.dto.UpdatePersonRequest;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service")
public interface UserServiceClient {

  @PostMapping("/persons")
  Map<String, Object> createPerson(@RequestBody CreatePersonRequest request);

  @PostMapping("/persons/{id}/activity")
  void updateLastActivity(@PathVariable("id") String id);

  @PutMapping("/persons/by-auth/{authUserId}")
  Map<String, Object> updatePersonByAuth(
      @PathVariable("authUserId") String authUserId, @RequestBody UpdatePersonRequest request);

  @GetMapping("/persons/by-auth/{authUserId}")
  Map<String, Object> getPersonByAuthUserId(@PathVariable("authUserId") String authUserId);

  @GetMapping("/persons/by-taxId/{taxId}")
  Map<String, Object> getPersonByTaxId(@PathVariable("taxId") String taxId);

  @DeleteMapping("/persons/{id}")
  void deletePerson(@PathVariable("id") String id);

  @DeleteMapping("/persons/by-auth/{authUserId}")
  void deleteByAuthUserId(@PathVariable("authUserId") String authUserId);
}
