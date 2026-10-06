package com.inmobiliaria.visit_calendar_service.repository;

import com.inmobiliaria.visit_calendar_service.model.AgentAvailability;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentAvailabilityRepository extends MongoRepository<AgentAvailability, String> {

  List<AgentAvailability> findByAgentId(String agentId);

  List<AgentAvailability> findByAgentIdAndType(String agentId, AgentAvailability.SlotType type);

  List<AgentAvailability> findByAgentIdAndTypeAndDayOfWeek(
      String agentId, AgentAvailability.SlotType type, DayOfWeek dayOfWeek);

  List<AgentAvailability> findByAgentIdAndTypeAndSpecificDate(
      String agentId, AgentAvailability.SlotType type, LocalDate specificDate);

  void deleteByAgentId(String agentId);

  void deleteByAgentIdAndType(String agentId, AgentAvailability.SlotType type);
}
