package com.inmobiliaria.contract_service.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "contract_versions")
public class ContractVersion {

  @Id private String id;

  /** ID de la operación/contrato al que pertenece esta versión */
  @Indexed private String operationId;

  /** Número secuencial de la versión (1, 2, 3, ...) */
  private Integer versionNumber;

  /** Contenido/texto completo del contrato */
  private String content;

  /** Título descriptivo del contrato para esta versión */
  private String title;

  /** Descripción del cambio realizado en esta versión */
  private String changeDescription;

  /** ID del autor que creó esta versión */
  private String authorId;

  /** Nombre del autor para mostrar en la UI */
  private String authorName;

  /** Rol del autor al momento de crear la versión */
  private String authorRole;

  /** Fecha y hora de creación de esta versión */
  private LocalDateTime createdAt;

  /** Estado de la versión: DRAFT, ACTIVE, SUPERSEDED */
  private String status;
}
