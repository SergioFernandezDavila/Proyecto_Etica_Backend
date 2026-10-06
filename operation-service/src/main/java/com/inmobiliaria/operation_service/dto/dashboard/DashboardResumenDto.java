package com.inmobiliaria.operation_service.dto.dashboard;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardResumenDto {
  private long totalInmueblesActivos;
  private long visitasProgramadasSemana;
  private long operacionesEnCurso;
}
