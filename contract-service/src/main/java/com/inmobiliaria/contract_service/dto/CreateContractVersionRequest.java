package com.inmobiliaria.contract_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// ============================================================
// REQUEST DTO - Crear nueva versión de contrato
// ============================================================
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateContractVersionRequest {

  @NotBlank(message = "El contenido del contrato no puede estar vacío")
  private String content;

  @NotBlank(message = "El título no puede estar vacío")
  private String title;

  private String changeDescription;

  // Extraídos del JWT por el servicio (no los envía el cliente)
  private String authorId;
  private String authorName;
  private String authorRole;
}
