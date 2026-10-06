package com.inmobiliaria.operation_service.service;

import com.inmobiliaria.operation_service.domain.OperationDocument;
import com.inmobiliaria.operation_service.dto.AgentRankingResponse;
import com.inmobiliaria.operation_service.repository.OperationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportService {

  private final OperationRepository operationRepository;

  public AgentRankingResponse getAgentRanking(
      LocalDateTime start, LocalDateTime end, String department) {
    List<OperationDocument> closedOps =
        operationRepository.findByStatusAndClosureDateBetween("CLOSED", start, end);

    // Filter by department if provided
    if (department != null && !department.isEmpty()) {
      closedOps =
          closedOps.stream()
              .filter(op -> department.equalsIgnoreCase(op.getDepartment()))
              .collect(Collectors.toList());
    }

    // Aggregate by agent
    Map<String, List<OperationDocument>> opsByAgent =
        closedOps.stream().collect(Collectors.groupingBy(OperationDocument::getAgentId));

    long totalSales = closedOps.size();

    List<AgentRankingResponse.AgentRankingItem> items = new ArrayList<>();

    opsByAgent.forEach(
        (agentId, agentOps) -> {
          String agentName = agentOps.get(0).getAgentName();
          long count = agentOps.size();
          double percentage = totalSales > 0 ? (count * 100.0) / totalSales : 0;

          items.add(
              AgentRankingResponse.AgentRankingItem.builder()
                  .agentId(agentId)
                  .agentName(agentName)
                  .closedSales(count)
                  .percentageOfTotal(Math.round(percentage * 10.0) / 10.0)
                  .build());
        });

    items.sort(
        Comparator.comparingLong(AgentRankingResponse.AgentRankingItem::getClosedSales).reversed());

    for (int i = 0; i < items.size(); i++) {
      items.get(i).setPosition(i + 1);
    }

    return AgentRankingResponse.builder()
        .ranking(items)
        .totalClosedSales(totalSales)
        .filters(
            AgentRankingResponse.Filters.builder()
                .startDate(start.toString())
                .endDate(end.toString())
                .department(department)
                .build())
        .build();
  }
}
