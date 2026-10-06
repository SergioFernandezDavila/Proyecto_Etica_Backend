package com.inmobiliaria.property_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmobiliaria.property_service.config.SecurityConfig;
import com.inmobiliaria.property_service.config.WebMvcConfig;
import com.inmobiliaria.property_service.domain.OperationType;
import com.inmobiliaria.property_service.dto.request.PropertyRequest;
import com.inmobiliaria.property_service.dto.response.PropertyResponse;
import com.inmobiliaria.property_service.dto.response.ResponsableResponse;
import com.inmobiliaria.property_service.dto.response.ResponseFactory;
import com.inmobiliaria.property_service.exception.GlobalExceptionHandler;
import com.inmobiliaria.property_service.exception.ResourceNotFoundException;
import com.inmobiliaria.property_service.repository.PropertyRepository;
import com.inmobiliaria.property_service.service.PropertyMetricsService;
import com.inmobiliaria.property_service.service.PropertyService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = PropertyController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {SecurityConfig.class, WebMvcConfig.class}))
@ActiveProfiles("test")
@Import({
  PropertyControllerTest.TestSecurityConfig.class,
  ResponseFactory.class,
  GlobalExceptionHandler.class
})
@DisplayName("PropertyController Unit Tests")
class PropertyControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private PropertyService propertyService;

  @MockitoBean private PropertyMetricsService propertyMetricsService;

  @MockitoBean private PropertyRepository propertyRepository;

  private PropertyRequest testPropertyRequest;
  private PropertyResponse testPropertyResponse;

  @TestConfiguration
  static class TestSecurityConfig {
    @Bean
    @Primary
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
      http.authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
          .csrf(csrf -> csrf.disable());
      return http.build();
    }
  }

  @BeforeEach
  @SuppressWarnings("unused")
  void setUp() {
    testPropertyRequest =
        new PropertyRequest(
            "Test Property",
            "123 Test St",
            "Downtown",
            100000.0,
            "Apartment",
            OperationType.VENTA,
            100.0,
            3,
            java.util.Set.of(),
            null);

    testPropertyResponse =
        new PropertyResponse(
            "test-id",
            "Test Property",
            "123 Test St",
            "Downtown",
            100000.0,
            "Apartment",
            OperationType.VENTA,
            100.0,
            3,
            "DISPONIBLE",
            "agent-123",
            null,
            "owner-123",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            java.util.Set.of(),
            null,
            null,
            null,
            null);

    when(propertyService.findById("test-id")).thenReturn(testPropertyResponse);
    when(propertyService.findById(eq("missing-id")))
        .thenThrow(new ResourceNotFoundException("Property"));
    when(propertyService.findByAgent("agent-123")).thenReturn(List.of(testPropertyResponse));
    when(propertyService.findByOwner("owner-123")).thenReturn(List.of(testPropertyResponse));
    when(propertyService.getResponsable("test-id"))
        .thenReturn(
            new ResponsableResponse("agent-123", "Agent Name", "agent@test.com", "555-1234"));
    when(propertyService.findWithFilters(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyList(),
            anyString(),
            anyString(),
            anyInt(),
            anyInt()))
        .thenReturn(Map.of("data", Collections.emptyList(), "totalElements", 0));
    when(propertyService.create(any(PropertyRequest.class), anyString()))
        .thenReturn(testPropertyResponse);
  }

  @Test
  @DisplayName("GET /properties should return list of properties")
  void testFindAllProperties() throws Exception {
    mockMvc
        .perform(get("/properties").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /properties/{id} should return property by id")
  void testFindPropertyById() throws Exception {
    mockMvc
        .perform(get("/properties/test-id").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /properties/agent/{agentId} should return agent's properties")
  void testFindPropertiesByAgent() throws Exception {
    mockMvc
        .perform(get("/properties/agent/agent-123").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("POST /properties should create property when agent header is provided")
  void testCreatePropertyRequiresAuth() throws Exception {
    mockMvc
        .perform(
            post("/properties")
                .header("X-Auth-User-Id", "agent-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testPropertyRequest)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("GET /properties/owner/{ownerId} should return owner's properties")
  void testFindPropertiesByOwner() throws Exception {
    mockMvc
        .perform(get("/properties/owner/owner-123").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /properties/{id}/status-history should return status history")
  void testGetStatusHistoryRequiresAuth() throws Exception {
    mockMvc
        .perform(get("/properties/test-id/status-history").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /properties/{id}/responsable should return responsable info")
  void testGetResponsable() throws Exception {
    mockMvc
        .perform(get("/properties/test-id/responsable").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }
}
