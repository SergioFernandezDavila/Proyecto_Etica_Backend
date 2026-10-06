package com.inmobiliaria.visit_calendar_service.dto;

import java.util.List;
import lombok.Data;

@Data
public class AvailabilityTemplateApplyRequest {
  private List<String> agentIds;
  private boolean overwrite;
}
