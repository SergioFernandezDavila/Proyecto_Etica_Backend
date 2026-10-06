package com.inmobiliaria.user_service.domain;

import java.time.LocalDate;
import lombok.*;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("employee")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeDocument extends PersonDocument {
  private String department;
  private String position;
  private LocalDate hireDate;

  @Builder
  public EmployeeDocument(
      String id,
      String authUserId,
      String firstName,
      String lastName,
      String fullName,
      java.time.LocalDate birthDate,
      String phone,
      String email,
      java.util.List<String> roleIds,
      boolean customRole,
      String assignedAgentId,
      String department,
      String position,
      LocalDate hireDate) {
    super(
        id,
        authUserId,
        firstName,
        lastName,
        fullName,
        birthDate,
        phone,
        email,
        PersonType.EMPLOYEE,
        roleIds,
        customRole,
        assignedAgentId);
    this.department = department;
    this.position = position;
    this.hireDate = hireDate;
  }
}
