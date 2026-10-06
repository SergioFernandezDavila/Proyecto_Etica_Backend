package com.inmobiliaria.property_service.client;

import com.inmobiliaria.property_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "identity-service", configuration = FeignConfig.class)
public interface IdentityClient {

  @GetMapping("/users/{id}")
  UserResponse findById(@PathVariable("id") String id);

  @PutMapping("/users/{id}")
  UserResponse updateUser(
      @PathVariable("id") String id,
      @org.springframework.web.bind.annotation.RequestBody UpdateUserRequest request);

  record UserResponse(
      String id,
      String status,
      String firstName,
      String lastName,
      String fullName,
      String email,
      String phone,
      String department) {}

  record UpdateUserRequest(
      String firstName,
      String lastName,
      String userType,
      java.time.LocalDate birthDate,
      String phone,
      String department,
      String position,
      java.time.LocalDate hireDate,
      String taxId,
      String preferredContactMethod,
      String assignedAgentId) {}
}
