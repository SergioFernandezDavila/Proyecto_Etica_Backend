package com.inmobiliaria.api_gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success, String message, T data, List<ApiError> errors, Meta meta) {
  public record ApiError(String field, String message, String code) {}

  public record Meta(Instant timestamp, String version) {}

  public static <T> ApiResponse<T> error(String message, String errorCode, String errorMessage) {
    return new ApiResponse<>(
        false,
        message,
        null,
        List.of(new ApiError(null, errorMessage, errorCode)),
        new Meta(Instant.now(), "v1"));
  }
}
