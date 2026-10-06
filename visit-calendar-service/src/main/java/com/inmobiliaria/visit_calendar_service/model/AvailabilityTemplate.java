package com.inmobiliaria.visit_calendar_service.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "availability_templates")
public class AvailabilityTemplate {

  @Id private String id;
  private String name; // e.g. "Standard Office Hours"
  private String description;

  @com.fasterxml.jackson.annotation.JsonProperty("isStandard")
  private boolean isStandard; // true for default standard template

  private List<TemplateSlot> slots;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TemplateSlot {
    private AgentAvailability.SlotType type;
    private DayOfWeek dayOfWeek;
    private LocalDate specificDate;
    private LocalTime startTime;
    private LocalTime endTime;

    @com.fasterxml.jackson.annotation.JsonProperty("isAvailable")
    private boolean isAvailable;
  }
}
