package com.inmobiliaria.visit_calendar_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.*;
import com.inmobiliaria.visit_calendar_service.exception.ScheduleConflictException;
import com.inmobiliaria.visit_calendar_service.model.CalendarEvent;
import com.inmobiliaria.visit_calendar_service.model.VisitRequest;
import com.inmobiliaria.visit_calendar_service.repository.CalendarEventRepository;
import com.inmobiliaria.visit_calendar_service.repository.VisitRepository;
import com.inmobiliaria.visit_calendar_service.repository.VisitRequestRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class VisitRequestServiceTest {

  @Mock private VisitRequestRepository visitRequestRepository;
  @Mock private CalendarEventRepository calendarEventRepository;
  @Mock private NotificationService notificationService;
  @Mock private VisitRepository visitRepository;
  @Mock private VehicleUsageService vehicleUsageService;
  @Mock private AgentAvailabilityService agentAvailabilityService;
  @Mock private VehicleService vehicleService;

  @InjectMocks private VisitRequestService visitRequestService;

  private VisitRequest sampleRequest;
  private Instant preferredTime;
  private Instant alternativeTime;

  @BeforeEach
  void setUp() {
    preferredTime = Instant.parse("2026-06-08T10:00:00Z");
    alternativeTime = Instant.parse("2026-06-08T16:00:00Z");

    sampleRequest =
        VisitRequest.builder()
            .id("req_123")
            .propertyId("prop_456")
            .propertyName("Apartamento Test")
            .agentId("agent_789")
            .agentName("John Agent")
            .clientId("client_000")
            .clientName("Mary Client")
            .preferredDateTime(preferredTime)
            .alternativeDateTime(alternativeTime)
            .status(VisitRequest.RequestStatus.PENDING)
            .build();
  }

  @Test
  void acceptVisitRequest_usesPreferredTime_whenNoConflicts() {
    when(visitRequestRepository.findById("req_123")).thenReturn(Optional.of(sampleRequest));

    // No availability conflicts, no busy events, no property conflicts
    doNothing().when(agentAvailabilityService).checkAgentAvailability(any(), any(), any());
    when(calendarEventRepository.findConflictingEventsForAgent(any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(calendarEventRepository.findConflictingEventsForNew(any(), any(), any()))
        .thenReturn(new ArrayList<>());

    // Mock save
    when(calendarEventRepository.save(any(CalendarEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(visitRequestRepository.save(any(VisitRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    VisitRequestResponse response =
        visitRequestService.acceptVisitRequest("req_123", "agent_789", null);

    assertNotNull(response);
    assertEquals(VisitRequest.RequestStatus.ACCEPTED, response.getStatus());
    verify(agentAvailabilityService)
        .checkAgentAvailability(eq("agent_789"), eq(preferredTime), any());
  }

  @Test
  void acceptVisitRequest_usesAlternativeTime_whenPreferredHasConflict() {
    when(visitRequestRepository.findById("req_123")).thenReturn(Optional.of(sampleRequest));

    // Preferred has conflict
    doThrow(new ScheduleConflictException("Outside working hours"))
        .when(agentAvailabilityService)
        .checkAgentAvailability(eq("agent_789"), eq(preferredTime), any());

    // Alternative has no conflicts
    doNothing()
        .when(agentAvailabilityService)
        .checkAgentAvailability(eq("agent_789"), eq(alternativeTime), any());
    when(calendarEventRepository.findConflictingEventsForAgent(
            eq("agent_789"), eq(alternativeTime), any()))
        .thenReturn(new ArrayList<>());
    when(calendarEventRepository.findConflictingEventsForNew(
            eq("prop_456"), eq(alternativeTime), any()))
        .thenReturn(new ArrayList<>());

    // Mock save
    when(calendarEventRepository.save(any(CalendarEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(visitRequestRepository.save(any(VisitRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    VisitRequestResponse response =
        visitRequestService.acceptVisitRequest("req_123", "agent_789", null);

    assertNotNull(response);
    assertEquals(VisitRequest.RequestStatus.ACCEPTED, response.getStatus());
    verify(agentAvailabilityService)
        .checkAgentAvailability(eq("agent_789"), eq(alternativeTime), any());
  }

  @Test
  void acceptVisitRequest_throwsScheduleConflict_whenBothHaveConflicts() {
    when(visitRequestRepository.findById("req_123")).thenReturn(Optional.of(sampleRequest));

    // Both have conflicts
    doThrow(new ScheduleConflictException("Outside working hours"))
        .when(agentAvailabilityService)
        .checkAgentAvailability(any(), any(), any());

    assertThrows(
        ScheduleConflictException.class,
        () -> visitRequestService.acceptVisitRequest("req_123", "agent_789", null));
  }

  @Test
  void acceptVisitRequest_usesCustomTime_whenProvided() {
    when(visitRequestRepository.findById("req_123")).thenReturn(Optional.of(sampleRequest));

    Instant customStart = Instant.parse("2026-06-09T09:00:00Z");
    Instant customEnd = Instant.parse("2026-06-09T10:00:00Z");
    AcceptVisitRequestDTO customDTO =
        AcceptVisitRequestDTO.builder()
            .customStartTime(customStart)
            .customEndTime(customEnd)
            .vehicleId("veh_111")
            .build();

    // No availability conflicts, no busy events, no property conflicts
    doNothing().when(agentAvailabilityService).checkAgentAvailability(any(), any(), any());
    when(calendarEventRepository.findConflictingEventsForAgent(any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(calendarEventRepository.findConflictingEventsForNew(any(), any(), any()))
        .thenReturn(new ArrayList<>());

    // Mock save
    when(calendarEventRepository.save(any(CalendarEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(visitRequestRepository.save(any(VisitRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    VisitRequestResponse response =
        visitRequestService.acceptVisitRequest("req_123", "agent_789", customDTO);

    assertNotNull(response);
    assertEquals(VisitRequest.RequestStatus.ACCEPTED, response.getStatus());
    verify(agentAvailabilityService)
        .checkAgentAvailability(eq("agent_789"), eq(customStart), eq(customEnd));
    verify(vehicleService)
        .checkVehicleAvailability(eq("veh_111"), eq(customStart), eq(customEnd), any());
  }
}
