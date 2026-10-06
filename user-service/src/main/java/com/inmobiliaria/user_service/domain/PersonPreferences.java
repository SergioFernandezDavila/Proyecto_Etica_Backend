package com.inmobiliaria.user_service.domain;

import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonPreferences {
  private List<String> preferredZones;
  private Integer minRooms;
  private Integer maxRooms;
  private Double maxPrice;
  private String preferredPropertyType;
}
