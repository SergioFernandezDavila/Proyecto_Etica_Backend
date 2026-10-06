package com.inmobiliaria.property_service.domain;

import java.time.Instant;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PriceHistory {
  private Double oldPrice;
  private Double newPrice;
  private Instant changedAt;
  private String changedBy;
}
