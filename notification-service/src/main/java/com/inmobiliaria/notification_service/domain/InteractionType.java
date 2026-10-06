package com.inmobiliaria.notification_service.domain;

public enum InteractionType {
  AGENT_AGENT, // comunicación entre agentes
  ADMIN_OP, // acciones administrativas/operativas
  INTERES, // cliente interesado (match de propiedades, etc.)
  PROPIEDAD_MOD, // modificación de propiedad (status, precio, etc.)
  VISITA // agendamiento, cancelación, resultado de visita
}
