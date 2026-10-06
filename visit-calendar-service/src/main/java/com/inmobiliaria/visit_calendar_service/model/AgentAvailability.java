package com.inmobiliaria.visit_calendar_service.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "agent_availabilities")
@CompoundIndexes({
  @CompoundIndex(name = "agent_recurring_idx", def = "{'agentId': 1, 'type': 1, 'dayOfWeek': 1}"),
  @CompoundIndex(name = "agent_exception_idx", def = "{'agentId': 1, 'type': 1, 'specificDate': 1}")
})
public class AgentAvailability {

  @Id private String id;
  private String agentId;

  private SlotType type; // RECURRING, EXCEPTION

  private DayOfWeek dayOfWeek; // MONDAY, TUESDAY, etc. (Used if type == RECURRING)

  private LocalDate specificDate; // e.g. 2026-12-25 (Used if type == EXCEPTION)

  private LocalTime startTime; // e.g. 08:30
  private LocalTime endTime; // e.g. 12:30

  @com.fasterxml.jackson.annotation.JsonProperty("isAvailable")
  private boolean isAvailable; // true = working hours, false = blocked/holiday/leave

  private String notes;

  public enum SlotType {
    RECURRING,
    EXCEPTION
  }
}
