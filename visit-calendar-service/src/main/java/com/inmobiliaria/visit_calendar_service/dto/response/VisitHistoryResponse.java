package com.inmobiliaria.visit_calendar_service.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO para el historial de visitas de una propiedad.
 *
 * <p>Incluye: - Lista paginada de visitas completadas - Estadísticas: conteo y porcentaje de
 * visitas con resultado "INTERESADO" - Información de paginación
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitHistoryResponse {

  /** Lista de visitas de la propiedad (página actual) */
  private List<VisitResponse> visits;

  /** Total de visitas realizadas en la propiedad (sin paginación) */
  private Long totalVisits;

  /** Conteo de visitas con resultado "INTERESADO" */
  private Long interestedCount;

  /** Porcentaje de visitas con resultado "INTERESADO" sobre el total */
  private BigDecimal interestedPercentage;

  /** Número de página actual (comenzando en 0) */
  private Integer pageNumber;

  /** Tamaño de la página (10, 20 o 30 elementos) */
  private Integer pageSize;

  /** Total de páginas disponibles */
  private Integer totalPages;

  /** Mensaje descriptivo cuando no hay visitas */
  private String message;
}
