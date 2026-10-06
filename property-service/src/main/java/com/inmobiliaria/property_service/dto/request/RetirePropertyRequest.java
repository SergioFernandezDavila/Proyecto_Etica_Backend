package com.inmobiliaria.property_service.dto.request;

import com.inmobiliaria.property_service.domain.RetirementReason;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RetirePropertyRequest {
  @NotNull(message = "El motivo de retiro es obligatorio")
  private RetirementReason motivoRetiro;

  private String detalleRetiro; // opcional
}
