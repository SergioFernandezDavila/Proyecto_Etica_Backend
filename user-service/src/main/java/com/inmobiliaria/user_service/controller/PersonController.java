package com.inmobiliaria.user_service.controller;

import com.inmobiliaria.user_service.dto.request.CreateEmployeeRequest;
import com.inmobiliaria.user_service.dto.request.CreateInterestedClientRequest;
import com.inmobiliaria.user_service.dto.request.CreateOwnerRequest;
import com.inmobiliaria.user_service.dto.request.CreatePersonRequest;
import com.inmobiliaria.user_service.dto.request.UpdatePersonRequest;
import com.inmobiliaria.user_service.dto.response.ApiResponse;
import com.inmobiliaria.user_service.dto.response.PersonResponse;
import com.inmobiliaria.user_service.dto.response.ResponseFactory;
import com.inmobiliaria.user_service.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/persons")
@RequiredArgsConstructor
public class PersonController {

  private final PersonService personService;
  private final AdminProfileService adminProfileService;
  private final EmployeeProfileService employeeProfileService;
  private final OwnerProfileService ownerProfileService;
  private final InterestedClientProfileService interestedClientProfileService;
  private final ResponseFactory responseFactory;

  @PostMapping
  public ResponseEntity<ApiResponse<PersonResponse>> create(
      @RequestBody @Valid CreatePersonRequest request) {
    PersonResponse data = adminProfileService.createAdminProfile(request);
    return ResponseEntity.status(201)
        .body(responseFactory.created("Person created successfully", data));
  }

  @PostMapping("/employees")
  public ResponseEntity<ApiResponse<PersonResponse>> createEmployee(
      @RequestBody @Valid CreateEmployeeRequest request) {
    PersonResponse data = employeeProfileService.createEmployeeProfile(request);
    return ResponseEntity.status(201)
        .body(responseFactory.created("Employee created successfully", data));
  }

  @PostMapping("/owners")
  public ResponseEntity<ApiResponse<PersonResponse>> createOwner(
      @RequestBody @Valid CreateOwnerRequest request) {
    PersonResponse data = ownerProfileService.createOwnerProfile(request);
    return ResponseEntity.status(201)
        .body(responseFactory.created("Owner created successfully", data));
  }

  @PostMapping("/clients-interested")
  public ResponseEntity<ApiResponse<PersonResponse>> createInterestedClient(
      @RequestBody @Valid CreateInterestedClientRequest request) {
    PersonResponse data = interestedClientProfileService.createInterestedClientProfile(request);
    return ResponseEntity.status(201)
        .body(responseFactory.created("Interested client created successfully", data));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<PersonResponse>>> findAll(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Boolean activo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int pageSize) {
    Page<PersonResponse> data = personService.findAll(type, activo, PageRequest.of(page, pageSize));
    return ResponseEntity.ok(responseFactory.paginated("Persons retrieved successfully", data));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PersonResponse>> findById(@PathVariable String id) {
    PersonResponse data = personService.findById(id);
    return ResponseEntity.ok(responseFactory.success("Person retrieved successfully", data));
  }

  @GetMapping("/by-auth/{authUserId}")
  public ResponseEntity<ApiResponse<PersonResponse>> findByAuthUserId(
      @PathVariable String authUserId) {
    PersonResponse data = personService.findByAuthUserId(authUserId);
    return ResponseEntity.ok(responseFactory.success("Person retrieved successfully", data));
  }

  @GetMapping("/by-taxId/{taxId}")
  public ResponseEntity<ApiResponse<PersonResponse>> findByTaxId(@PathVariable String taxId) {
    PersonResponse data = personService.findPersonByTaxId(taxId);
    return ResponseEntity.ok(responseFactory.success("Person retrieved successfully", data));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<PersonResponse>> update(
      @PathVariable String id, @RequestBody UpdatePersonRequest request) {
    PersonResponse data = personService.update(id, request);
    return ResponseEntity.ok(responseFactory.success("Person updated successfully", data));
  }

  @PutMapping("/by-auth/{authUserId}")
  public ResponseEntity<ApiResponse<PersonResponse>> updateByAuthUserId(
      @PathVariable String authUserId, @RequestBody UpdatePersonRequest request) {
    PersonResponse data = personService.updateByAuthUserId(authUserId, request);
    return ResponseEntity.ok(responseFactory.success("Person updated successfully", data));
  }

  @PostMapping("/{id}/assign-role")
  public ResponseEntity<ApiResponse<PersonResponse>> assignRoles(
      @PathVariable String id,
      @RequestBody List<String> roleIds,
      @RequestParam(defaultValue = "false") boolean isCustom) {
    PersonResponse data = personService.assignRoles(id, roleIds, isCustom);
    return ResponseEntity.ok(responseFactory.success("Roles assigned successfully", data));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
    personService.deleteById(id);
    return ResponseEntity.status(204).body(responseFactory.deleted("Person deleted successfully"));
  }

  @DeleteMapping("/by-auth/{authUserId}")
  public ResponseEntity<ApiResponse<Void>> deleteByAuthUserId(@PathVariable String authUserId) {
    personService.deleteByAuthUserId(authUserId);
    return ResponseEntity.status(204).body(responseFactory.deleted("Person deleted successfully"));
  }

  @GetMapping("/agents/clients")
  public ResponseEntity<ApiResponse<List<PersonResponse>>> getClientsForAgent(
      HttpServletRequest request) {
    String agentId = request.getHeader("X-Auth-User-Id");
    if (agentId == null) {
      throw new IllegalArgumentException("Missing X-Auth-User-Id header");
    }
    List<PersonResponse> data = personService.getClientsForAgent(agentId);
    return ResponseEntity.ok(responseFactory.success("Clients retrieved successfully", data));
  }

  @GetMapping("/agents/owners")
  public ResponseEntity<ApiResponse<List<PersonResponse>>> getOwnersForAgent(
      HttpServletRequest request) {
    String agentId = request.getHeader("X-Auth-User-Id");
    if (agentId == null) {
      throw new IllegalArgumentException("Missing X-Auth-User-Id header");
    }
    List<PersonResponse> data = personService.getOwnersForAgent(agentId);
    return ResponseEntity.ok(responseFactory.success("Owners retrieved successfully", data));
  }

  @PostMapping("/agents/clients")
  public ResponseEntity<ApiResponse<PersonResponse>> createClientForAgent(
      HttpServletRequest request, @RequestBody @Valid CreateInterestedClientRequest clientRequest) {
    String agentId = request.getHeader("X-Auth-User-Id");
    if (agentId == null) {
      throw new IllegalArgumentException("Missing X-Auth-User-Id header");
    }
    PersonResponse data = personService.createClientForAgent(agentId, clientRequest);
    return ResponseEntity.status(201)
        .body(responseFactory.created("Client created and assigned to agent successfully", data));
  }

  @PutMapping("/agents/clients/{clientId}")
  public ResponseEntity<ApiResponse<PersonResponse>> updateClientForAgent(
      HttpServletRequest request,
      @PathVariable String clientId,
      @RequestBody UpdatePersonRequest updateRequest) {
    String agentId = request.getHeader("X-Auth-User-Id");
    if (agentId == null) {
      throw new IllegalArgumentException("Missing X-Auth-User-Id header");
    }
    PersonResponse data = personService.updateClientForAgent(agentId, clientId, updateRequest);
    return ResponseEntity.ok(responseFactory.success("Client updated successfully", data));
  }

  @PutMapping("/{id}/deactivate")
  public ResponseEntity<ApiResponse<PersonResponse>> darDeBaja(
      @PathVariable String id,
      @RequestParam String motivo,
      @RequestHeader("X-Auth-User-Id") String requesterId) {
    PersonResponse data = personService.darDeBaja(id, motivo, requesterId);
    return ResponseEntity.ok(responseFactory.success("Person deactivated successfully", data));
  }

  @GetMapping("/inactivos")
  public ResponseEntity<ApiResponse<List<PersonResponse>>> findInactivos(
      @RequestParam(defaultValue = "90") int diasSinActividad) {
    java.time.LocalDate fechaLimite = java.time.LocalDate.now().minusDays(diasSinActividad);
    List<PersonResponse> data = personService.findClientesInactivos(fechaLimite);
    return ResponseEntity.ok(
        responseFactory.success("Inactive clients retrieved successfully", data));
  }
}
