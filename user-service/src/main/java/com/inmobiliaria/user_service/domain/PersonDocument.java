package com.inmobiliaria.user_service.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "persons")
@TypeAlias("person")
@Getter
@Setter
@NoArgsConstructor
public abstract class PersonDocument extends BaseDocument {

  @Id private String id;

  @Indexed(unique = true)
  private String authUserId;

  private String firstName;
  private String lastName;
  private String fullName;
  private LocalDate birthDate;
  private String phone;
  private String email;

  private PersonType personType;

  private List<String> roleIds;
  private boolean customRole;

  @Indexed private String assignedAgentId;

  private List<AuditEntry> auditLog = new ArrayList<>();

  private PersonPreferences preferences;

  // Constructor explícito con los 12 campos que usan las subclases en super(...)
  // auditLog queda fuera — se inicializa solo con new ArrayList<>()
  public PersonDocument(
      String id,
      String authUserId,
      String firstName,
      String lastName,
      String fullName,
      LocalDate birthDate,
      String phone,
      String email,
      PersonType personType,
      List<String> roleIds,
      boolean customRole,
      String assignedAgentId) {
    this.id = id;
    this.authUserId = authUserId;
    this.firstName = firstName;
    this.lastName = lastName;
    this.fullName = fullName;
    this.birthDate = birthDate;
    this.phone = phone;
    this.email = email;
    this.personType = personType;
    this.roleIds = roleIds;
    this.customRole = customRole;
    this.assignedAgentId = assignedAgentId;
    this.auditLog = new ArrayList<>();
  }
}
