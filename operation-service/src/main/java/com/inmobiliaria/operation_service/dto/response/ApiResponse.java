package com.inmobiliaria.operation_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Standardized API response wrapper following the contract defined in EndpointImprovement.md. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  public static final String API_VERSION = "v1";

  private boolean success;
  private String message;
  private T data;
  private List<ApiError> errors;
  private Meta meta;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ApiError {
    private String field;
    private String message;
    private String code;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Meta {
    private Instant timestamp;
    private String version;
    private Integer page;
    private Integer limit;
    private Long total;
    private String requestId;
  }

  // ─── Success factory methods ───

  public static <T> ApiResponse<T> success(String message, T data) {
    return ApiResponse.<T>builder()
        .success(true)
        .message(message)
        .data(data)
        .errors(null)
        .meta(Meta.builder().timestamp(Instant.now()).version(API_VERSION).build())
        .build();
  }

  public static <T> ApiResponse<T> success(String message) {
    return success(message, null);
  }

  public static <T> ApiResponse<T> success(String message, T data, Meta meta) {
    ApiResponse<T> response = success(message, data);
    response.setMeta(meta);
    return response;
  }

  // ─── Error factory methods ───

  public static <T> ApiResponse<T> error(String message, List<ApiError> errors) {
    return ApiResponse.<T>builder()
        .success(false)
        .message(message)
        .data(null)
        .errors(errors)
        .meta(Meta.builder().timestamp(Instant.now()).version(API_VERSION).build())
        .build();
  }

  public static <T> ApiResponse<T> error(String message) {
    return error(message, List.of());
  }

  public static <T> ApiResponse<T> error(
      String message, String field, String errorCode, String errorMessage) {
    return error(
        message,
        List.of(ApiError.builder().field(field).message(errorMessage).code(errorCode).build()));
  }
}
