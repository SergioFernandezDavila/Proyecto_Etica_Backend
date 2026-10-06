package com.inmobiliaria.user_service.domain;

import java.util.List;
import lombok.*;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("interested_client")
@Getter
@Setter
@NoArgsConstructor
public class InterestedClientDocument extends PersonDocument {
  private String preferredContactMethod;
  private String budget;
  private List<String> interestedPropertyIds;
  private String preferredZone;
  private String preferredPropertyType;
  private Integer preferredRooms;
  private boolean activo = true;
  private java.time.LocalDate lastActivityDate;
  private java.time.LocalDate fechaBaja;
  private String motivoBaja;

  @Builder
  public InterestedClientDocument(
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
      String preferredContactMethod,
      String budget,
      List<String> interestedPropertyIds,
      String preferredZone,
      String preferredPropertyType,
      Integer preferredRooms,
      java.time.LocalDate lastActivityDate) {
    super(
        id,
        authUserId,
        firstName,
        lastName,
        fullName,
        birthDate,
        phone,
        email,
        PersonType.INTERESTED_CLIENT,
        roleIds,
        customRole,
        assignedAgentId);
    this.preferredContactMethod = preferredContactMethod;
    this.budget = budget;
    this.interestedPropertyIds = interestedPropertyIds;
    this.preferredZone = preferredZone;
    this.preferredPropertyType = preferredPropertyType;
    this.preferredRooms = preferredRooms;
    this.lastActivityDate = lastActivityDate;
  }
}
