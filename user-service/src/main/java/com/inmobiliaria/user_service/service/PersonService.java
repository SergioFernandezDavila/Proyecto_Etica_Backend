package com.inmobiliaria.user_service.service;

import com.inmobiliaria.user_service.client.AccessControlClient;
import com.inmobiliaria.user_service.domain.*;
import com.inmobiliaria.user_service.dto.request.CreateInterestedClientRequest;
import com.inmobiliaria.user_service.dto.request.CreatePersonRequest;
import com.inmobiliaria.user_service.dto.request.SearchPreferencesRequest;
import com.inmobiliaria.user_service.dto.request.UpdatePersonRequest;
import com.inmobiliaria.user_service.dto.response.PersonResponse;
import com.inmobiliaria.user_service.exception.ResourceAlreadyExistsException;
import com.inmobiliaria.user_service.exception.ResourceNotFoundException;
import com.inmobiliaria.user_service.repository.AuditLogRepository;
import com.inmobiliaria.user_service.repository.PersonRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonService {

  private final PersonRepository personRepository;
  private final AccessControlClient accessControlClient;
  private final AuditLogRepository auditLogRepository;

  public PersonResponse create(CreatePersonRequest request) {
    log.info("Creating person profile for authUserId: {}", request.authUserId());

    if (personRepository.existsByAuthUserId(request.authUserId())) {
      throw new ResourceAlreadyExistsException(
          "Profile already exists for authUserId: " + request.authUserId());
    }

    if (request.roleIds() != null && !request.roleIds().isEmpty()) {
      boolean validRoles = accessControlClient.validateRoleIds(request.roleIds());
      if (!validRoles) {
        throw new ResourceNotFoundException("One or more role IDs are invalid");
      }
    }

    PersonDocument document;
    switch (request.personType()) {
      case ADMIN ->
          document =
              AdminDocument.builder()
                  .authUserId(request.authUserId())
                  .firstName(request.firstName())
                  .lastName(request.lastName())
                  .fullName(request.firstName() + " " + request.lastName())
                  .birthDate(request.birthDate())
                  .phone(request.phone())
                  .email(request.email())
                  .roleIds(request.roleIds())
                  .assignedAgentId(request.assignedAgentId())
                  .build();
      case EMPLOYEE ->
          document =
              EmployeeDocument.builder()
                  .authUserId(request.authUserId())
                  .firstName(request.firstName())
                  .lastName(request.lastName())
                  .fullName(request.firstName() + " " + request.lastName())
                  .birthDate(request.birthDate())
                  .phone(request.phone())
                  .email(request.email())
                  .roleIds(request.roleIds())
                  .assignedAgentId(request.assignedAgentId())
                  .department(request.department())
                  .position(request.position())
                  .hireDate(request.hireDate())
                  .build();
      case OWNER ->
          document =
              OwnerDocument.builder()
                  .authUserId(request.authUserId())
                  .firstName(request.firstName())
                  .lastName(request.lastName())
                  .fullName(request.firstName() + " " + request.lastName())
                  .birthDate(request.birthDate())
                  .phone(request.phone())
                  .email(request.email())
                  .roleIds(request.roleIds())
                  .assignedAgentId(request.assignedAgentId())
                  .taxId(request.taxId())
                  .address(request.address())
                  .propertyIds(request.propertyIds())
                  .build();
      case INTERESTED_CLIENT ->
          document =
              InterestedClientDocument.builder()
                  .authUserId(request.authUserId())
                  .firstName(request.firstName())
                  .lastName(request.lastName())
                  .fullName(request.firstName() + " " + request.lastName())
                  .birthDate(request.birthDate())
                  .phone(request.phone())
                  .email(request.email())
                  .roleIds(request.roleIds())
                  .assignedAgentId(request.assignedAgentId())
                  .preferredContactMethod(request.preferredContactMethod())
                  .budget(request.budget())
                  .preferredZone(request.preferredZone())
                  .preferredPropertyType(request.preferredPropertyType())
                  .preferredRooms(request.preferredRooms())
                  .build();
      default ->
          throw new IllegalArgumentException("Unsupported person type: " + request.personType());
    }

    document.setCreatedAt(Instant.now());
    document.setUpdatedAt(Instant.now());
    document.setCreatedBy("system");

    PersonDocument saved = personRepository.save(document);

    auditLogRepository.save(
        AuditLogDocument.builder()
            .timestamp(Instant.now())
            .changedBy(getCurrentUserId())
            .action("CREATED")
            .personId(saved.getId())
            .personName(saved.getFullName())
            .personType(saved.getPersonType().name())
            .changes(null)
            .build());

    return mapToResponse(saved);
  }

  public Page<PersonResponse> findAll(String type, Boolean activo, Pageable pageable) {
    List<PersonDocument> all;

    if (type != null && !type.isBlank()) {
      try {
        PersonType personType = PersonType.valueOf(type.toUpperCase());
        all = personRepository.findByPersonType(personType);
      } catch (IllegalArgumentException e) {
        log.warn("Invalid person type: {}", type);
        all = personRepository.findAll();
      }
    } else {
      all = personRepository.findAll();
    }

    Stream<PersonDocument> stream = all.stream();

    if (activo != null) {
      stream =
          stream.filter(
              p -> {
                if (p instanceof InterestedClientDocument client) {
                  return client.isActivo() == activo;
                }
                return true; // Solo InterestedClientDocument tiene el campo activo en este modelo
              });
    }

    List<PersonResponse> filtered = stream.map(this::mapToResponse).collect(Collectors.toList());

    int start = (int) pageable.getOffset();
    int end = Math.min((start + pageable.getPageSize()), filtered.size());

    if (start > filtered.size()) {
      return new PageImpl<>(List.of(), pageable, filtered.size());
    }

    return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
  }

  public PersonResponse findById(String id) {
    return personRepository
        .findById(id)
        .map(this::mapToResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Person not found with id: " + id));
  }

  public PersonResponse findByAuthUserId(String authUserId) {
    return personRepository
        .findByAuthUserId(authUserId)
        .map(this::mapToResponse)
        .orElseThrow(
            () -> new ResourceNotFoundException("Person not found with authUserId: " + authUserId));
  }

  public PersonResponse findPersonByTaxId(String taxId) {
    return personRepository
        .findByTaxId(taxId)
        .map(this::mapToResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Person not found with taxId: " + taxId));
  }

  public PersonResponse update(String id, UpdatePersonRequest request) {
    PersonDocument person =
        personRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Person not found with id: " + id));

    List<AuditEntry.FieldChange> changes = new ArrayList<>();

    if (request.firstName() != null && !request.firstName().equals(person.getFirstName())) {
      changes.add(
          AuditEntry.FieldChange.builder()
              .field("firstName")
              .oldValue(person.getFirstName())
              .newValue(request.firstName())
              .build());
      person.setFirstName(request.firstName());
    }
    if (request.lastName() != null && !request.lastName().equals(person.getLastName())) {
      changes.add(
          AuditEntry.FieldChange.builder()
              .field("lastName")
              .oldValue(person.getLastName())
              .newValue(request.lastName())
              .build());
      person.setLastName(request.lastName());
    }
    if (person.getFirstName() != null && person.getLastName() != null) {
      person.setFullName(person.getFirstName() + " " + person.getLastName());
    }
    if (request.birthDate() != null && !request.birthDate().equals(person.getBirthDate())) {
      changes.add(
          AuditEntry.FieldChange.builder()
              .field("birthDate")
              .oldValue(person.getBirthDate() != null ? person.getBirthDate().toString() : null)
              .newValue(request.birthDate().toString())
              .build());
      person.setBirthDate(request.birthDate());
    }

    if (request.phone() != null && !request.phone().equals(person.getPhone())) {
      changes.add(
          AuditEntry.FieldChange.builder()
              .field("phone")
              .oldValue(person.getPhone())
              .newValue(request.phone())
              .build());
      person.setPhone(request.phone());
    }

    if (request.assignedAgentId() != null
        && !request.assignedAgentId().equals(person.getAssignedAgentId())) {
      changes.add(
          AuditEntry.FieldChange.builder()
              .field("assignedAgentId")
              .oldValue(person.getAssignedAgentId())
              .newValue(request.assignedAgentId())
              .build());
      person.setAssignedAgentId(request.assignedAgentId());
    }

    if (person instanceof EmployeeDocument emp) {
      if (request.department() != null && !request.department().equals(emp.getDepartment())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("department")
                .oldValue(emp.getDepartment())
                .newValue(request.department())
                .build());
        emp.setDepartment(request.department());
      }
      if (request.position() != null && !request.position().equals(emp.getPosition())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("position")
                .oldValue(emp.getPosition())
                .newValue(request.position())
                .build());
        emp.setPosition(request.position());
      }
      if (request.hireDate() != null && !request.hireDate().equals(emp.getHireDate())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("hireDate")
                .oldValue(emp.getHireDate() != null ? emp.getHireDate().toString() : null)
                .newValue(request.hireDate().toString())
                .build());
        emp.setHireDate(request.hireDate());
      }
    } else if (person instanceof OwnerDocument owner) {
      if (request.taxId() != null && !request.taxId().equals(owner.getTaxId())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("taxId")
                .oldValue(owner.getTaxId())
                .newValue(request.taxId())
                .build());
        owner.setTaxId(request.taxId());
      }
    } else if (person instanceof InterestedClientDocument client) {
      if (request.preferredContactMethod() != null)
        client.setPreferredContactMethod(request.preferredContactMethod());
      if (request.budget() != null) client.setBudget(request.budget());
      if (request.preferredZone() != null) client.setPreferredZone(request.preferredZone());
      if (request.preferredPropertyType() != null)
        client.setPreferredPropertyType(request.preferredPropertyType());
      if (request.preferredRooms() != null) client.setPreferredRooms(request.preferredRooms());
    }

    if (!changes.isEmpty()) {
      String changedBy = getCurrentUserId();

      auditLogRepository.save(
          AuditLogDocument.builder()
              .timestamp(Instant.now())
              .changedBy(changedBy)
              .action("UPDATED")
              .personId(person.getId())
              .personName(person.getFullName())
              .personType(person.getPersonType().name())
              .changes(changes)
              .build());

      AuditEntry entry =
          AuditEntry.builder()
              .changedAt(Instant.now())
              .changedBy(changedBy)
              .changes(changes)
              .build();

      if (person.getAuditLog() == null) person.setAuditLog(new ArrayList<>());
      person.getAuditLog().add(entry);
    }

    person.setUpdatedAt(Instant.now());
    return mapToResponse(personRepository.save(person));
  }

  public PersonResponse updateByAuthUserId(String authUserId, UpdatePersonRequest request) {
    PersonDocument person =
        personRepository
            .findByAuthUserId(authUserId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Person not found with authUserId: " + authUserId));
    return update(person.getId(), request);
  }

  public PersonResponse assignRoles(String id, List<String> roleIds, boolean isCustom) {
    PersonDocument person =
        personRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Person not found with id: " + id));

    boolean validRoles = accessControlClient.validateRoleIds(roleIds);
    if (!validRoles) {
      throw new ResourceNotFoundException("One or more role IDs are invalid");
    }

    person.setRoleIds(roleIds);
    person.setCustomRole(isCustom);
    person.setUpdatedAt(Instant.now());

    return mapToResponse(personRepository.save(person));
  }

  public void deleteById(String id) {
    if (!personRepository.existsById(id)) {
      throw new ResourceNotFoundException("Person not found with id: " + id);
    }
    personRepository.deleteById(id);
  }

  public void deleteByAuthUserId(String authUserId) {
    if (!personRepository.existsByAuthUserId(authUserId)) {
      throw new ResourceNotFoundException("Person not found with authUserId: " + authUserId);
    }
    personRepository.deleteByAuthUserId(authUserId);
  }

  public PersonResponse darDeBaja(String personId, String motivo, String changedBy) {
    PersonDocument person =
        personRepository
            .findById(personId)
            .orElseThrow(() -> new ResourceNotFoundException("Person not found: " + personId));

    if (!(person instanceof InterestedClientDocument client)) {
      throw new IllegalArgumentException("Solo se puede dar de baja a clientes interesados");
    }
    if (!client.isActivo()) {
      throw new IllegalStateException("El cliente ya está dado de baja");
    }

    client.setActivo(false);
    client.setFechaBaja(java.time.LocalDate.now());
    client.setMotivoBaja(motivo);

    PersonDocument saved = personRepository.save(client);

    auditLogRepository.save(
        AuditLogDocument.builder()
            .timestamp(java.time.Instant.now())
            .changedBy(changedBy)
            .action("BAJA")
            .personId(saved.getId())
            .personName(saved.getFullName())
            .personType("INTERESTED_CLIENT")
            .changes(
                List.of(
                    new AuditEntry.FieldChange("activo", "true", "false"),
                    new AuditEntry.FieldChange("motivoBaja", null, motivo)))
            .build());

    return mapToResponse(saved);
  }

  public List<PersonResponse> findClientesInactivos(java.time.LocalDate fechaLimite) {
    return personRepository.findClientesInactivosDespuesDe(fechaLimite).stream()
        .map(this::mapToResponse)
        .collect(java.util.stream.Collectors.toList());
  }

  public List<PersonResponse> getClientsForAgent(String agentId) {
    // Check if agent exists
    if (!personRepository.existsByAuthUserId(agentId)) {
      throw new ResourceNotFoundException("Agent not found with id: " + agentId);
    }

    return personRepository
        .findByAssignedAgentIdAndPersonType(agentId, PersonType.INTERESTED_CLIENT)
        .stream()
        .map(this::mapToResponse)
        .collect(Collectors.toList());
  }

  public List<PersonResponse> getOwnersForAgent(String agentId) {
    // Check if agent exists
    if (!personRepository.existsByAuthUserId(agentId)) {
      throw new ResourceNotFoundException("Agent not found with id: " + agentId);
    }

    return personRepository.findByAssignedAgentIdAndPersonType(agentId, PersonType.OWNER).stream()
        .map(this::mapToResponse)
        .collect(Collectors.toList());
  }

  public void updateLastActivityDate(String authUserId) {
    PersonDocument person =
        personRepository
            .findByAuthUserId(authUserId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Cliente no encontrado con authUserId: " + authUserId));
    if (person instanceof InterestedClientDocument client) {
      client.setLastActivityDate(java.time.LocalDate.now());
      personRepository.save(client);
    }
  }

  public void validarClienteActivo(String authUserId) {
    PersonDocument person =
        personRepository
            .findByAuthUserId(authUserId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Cliente no encontrado con authUserId: " + authUserId));
    if (person instanceof InterestedClientDocument client) {
      if (!client.isActivo()) {
        throw new IllegalStateException(
            "El cliente con id " + authUserId + " está dado de baja y no puede operar.");
      }
      client.setLastActivityDate(java.time.LocalDate.now());
      personRepository.save(client);
    } else {
      throw new IllegalStateException(
          "El usuario con authUserId " + authUserId + " no es un cliente interesado.");
    }
  }

  public PersonResponse createClientForAgent(
      String agentId, CreateInterestedClientRequest request) {
    // Check if agent exists
    if (!personRepository.existsByAuthUserId(agentId)) {
      throw new ResourceNotFoundException("Agent not found with id: " + agentId);
    }

    InterestedClientDocument client =
        InterestedClientDocument.builder()
            .authUserId(request.authUserId())
            .firstName(request.firstName())
            .lastName(request.lastName())
            .fullName(request.firstName() + " " + request.lastName())
            .birthDate(request.birthDate())
            .phone(request.phone())
            .email(request.email())
            .roleIds(List.of("rol_interested_client"))
            .assignedAgentId(agentId)
            .preferredContactMethod(request.preferredContactMethod())
            .budget(request.budget())
            .build();

    client.setCreatedAt(Instant.now());
    client.setUpdatedAt(Instant.now());
    client.setCreatedBy(agentId);

    client = personRepository.save(client);
    return mapToResponse(client);
  }

  public PersonResponse updateClientForAgent(
      String agentId, String clientId, UpdatePersonRequest request) {
    PersonDocument client =
        personRepository
            .findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

    if (client.getAssignedAgentId() == null || !client.getAssignedAgentId().equals(agentId)) {
      throw new ResourceNotFoundException("Client not found or not assigned to you");
    }

    if (request.firstName() != null) client.setFirstName(request.firstName());
    if (request.lastName() != null) client.setLastName(request.lastName());
    if (client.getFirstName() != null && client.getLastName() != null) {
      client.setFullName(client.getFirstName() + " " + client.getLastName());
    }
    if (request.birthDate() != null) client.setBirthDate(request.birthDate());
    if (request.phone() != null) client.setPhone(request.phone());

    if (client instanceof InterestedClientDocument interested) {
      if (request.preferredContactMethod() != null)
        interested.setPreferredContactMethod(request.preferredContactMethod());
      if (request.budget() != null) interested.setBudget(request.budget());
      if (request.preferredZone() != null) interested.setPreferredZone(request.preferredZone());
      if (request.preferredPropertyType() != null)
        interested.setPreferredPropertyType(request.preferredPropertyType());
      if (request.preferredRooms() != null) interested.setPreferredRooms(request.preferredRooms());
    }

    client.setUpdatedAt(Instant.now());
    return mapToResponse(personRepository.save(client));
  }

  private String getCurrentUserId() {
    try {
      jakarta.servlet.http.HttpServletRequest request =
          ((org.springframework.web.context.request.ServletRequestAttributes)
                  org.springframework.web.context.request.RequestContextHolder
                      .getRequestAttributes())
              .getRequest();
      String userId = request.getHeader("X-Auth-User-Id");
      return userId != null ? userId : "unknown";
    } catch (Exception e) {
      return "unknown";
    }
  }

  private PersonResponse mapToResponse(PersonDocument document) {
    String department = null,
        position = null,
        taxId = null,
        address = null,
        contact = null,
        budget = null,
        preferredZone = null,
        preferredPropertyType = null;
    List<String> propertyIds = null;
    LocalDate hireDate = null;
    Integer preferredRooms = null;
    List<String> preferredZones = null;
    Integer minRooms = null;
    Integer maxRooms = null;
    Double maxPrice = null;

    if (document instanceof EmployeeDocument employee) {
      department = employee.getDepartment();
      position = employee.getPosition();
      hireDate = employee.getHireDate();
    } else if (document instanceof OwnerDocument owner) {
      taxId = owner.getTaxId();
      address = owner.getAddress();
      propertyIds = owner.getPropertyIds();
    } else if (document instanceof InterestedClientDocument client) {
      contact = client.getPreferredContactMethod();
      budget = client.getBudget();
      preferredZone = client.getPreferredZone();
      preferredPropertyType = client.getPreferredPropertyType();
      preferredRooms = client.getPreferredRooms();

      // Si existe el objeto de preferencias estructurado, priorizarlo para la respuesta
      if (client.getPreferences() != null) {
        preferredZones = client.getPreferences().getPreferredZones();
        if (preferredZones != null && !preferredZones.isEmpty()) {
          preferredZone = preferredZones.get(0);
        }
        if (client.getPreferences().getPreferredPropertyType() != null) {
          preferredPropertyType = client.getPreferences().getPreferredPropertyType();
        }
        if (client.getPreferences().getMinRooms() != null) {
          preferredRooms = client.getPreferences().getMinRooms();
          minRooms = client.getPreferences().getMinRooms();
        }
        if (client.getPreferences().getMaxRooms() != null) {
          maxRooms = client.getPreferences().getMaxRooms();
        }
        if (client.getPreferences().getMaxPrice() != null) {
          maxPrice = client.getPreferences().getMaxPrice();
          budget = maxPrice.toString();
        }
      }
    }

    return new PersonResponse(
        document.getId(),
        document.getAuthUserId(),
        document.getFirstName(),
        document.getLastName(),
        document.getFullName(),
        document.getBirthDate(),
        document.getPhone(),
        document.getEmail(),
        document.getPersonType(),
        document.getRoleIds(),
        document.isCustomRole(),
        department,
        position,
        hireDate,
        taxId,
        address,
        propertyIds,
        contact,
        budget,
        preferredZone,
        preferredPropertyType,
        preferredRooms,
        preferredZones,
        minRooms,
        maxRooms,
        maxPrice);
  }

  public PersonResponse updateSearchPreferences(String personId, SearchPreferencesRequest request) {
    PersonDocument person =
        personRepository
            .findById(personId)
            .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));

    if (!(person instanceof InterestedClientDocument client)) {
      throw new IllegalArgumentException(
          "Solo se pueden registrar preferencias para clientes buscadores");
    }

    client.setPreferences(
        PersonPreferences.builder()
            .preferredZones(request.preferredZones())
            .minRooms(request.minRooms())
            .maxRooms(request.maxRooms())
            .maxPrice(request.maxPrice())
            .preferredPropertyType(request.preferredPropertyType())
            .build());
    client.setUpdatedAt(Instant.now());

    return mapToResponse(personRepository.save(client));
  }

  public PersonPreferences savePreferences(String personId, PersonPreferences preferences) {
    PersonDocument person =
        personRepository
            .findById(personId)
            .orElseGet(
                () ->
                    personRepository
                        .findByAuthUserId(personId)
                        .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Person not found with id or authUserId: " + personId)));

    person.setPreferences(preferences);

    person.setPreferences(preferences);
    PersonDocument savedPerson = personRepository.save(person);
    return savedPerson.getPreferences();
  }

  public PersonPreferences getPreferences(String personId) {
    PersonDocument person =
        personRepository
            .findById(personId)
            .orElseGet(
                () ->
                    personRepository
                        .findByAuthUserId(personId)
                        .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Person not found with id or authUserId: " + personId)));
    return person.getPreferences();
  }
}
