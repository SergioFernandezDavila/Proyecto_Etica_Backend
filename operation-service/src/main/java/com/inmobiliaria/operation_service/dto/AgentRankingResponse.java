package com.inmobiliaria.operation_service.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRankingResponse {
  private List<AgentRankingItem> ranking;
  private long totalClosedSales;
  private Filters filters;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AgentRankingItem {
    private int position;
    private String agentId;
    private String agentName;
    private long closedSales;
    private double percentageOfTotal;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Filters {
    private String startDate;
    private String endDate;
    private String department;
  }
}
