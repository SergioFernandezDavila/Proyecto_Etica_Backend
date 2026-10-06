package com.inmobiliaria.property_service.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Centralized factory for building standardized API responses. */
@Component
public class ResponseFactory {

  // ─── Success responses ───

  public <T> ApiResponse<T> success(String message, T data) {
    return ApiResponse.success(message, data);
  }

  public <T> ApiResponse<T> success(String message) {
    return ApiResponse.success(message, null);
  }

  public <T> ApiResponse<T> created(String message, T data) {
    return ApiResponse.success(message, data);
  }

  public <T> ApiResponse<T> deleted(String message) {
    return ApiResponse.success(message, null);
  }

  public <T> ApiResponse<List<T>> paginated(String message, Page<T> page) {
    ApiResponse.Meta meta =
        ApiResponse.Meta.builder()
            .timestamp(java.time.Instant.now())
            .version(ApiResponse.API_VERSION)
            .page(page.getNumber())
            .limit(page.getSize())
            .total(page.getTotalElements())
            .build();
    return ApiResponse.success(message, page.getContent(), meta);
  }

  public <T> ApiResponse<List<T>> paginated(
      String message, List<T> content, int page, int limit, long total) {
    ApiResponse.Meta meta =
        ApiResponse.Meta.builder()
            .timestamp(java.time.Instant.now())
            .version(ApiResponse.API_VERSION)
            .page(page)
            .limit(limit)
            .total(total)
            .build();
    return ApiResponse.success(message, content, meta);
  }

  // ─── Error responses ───

  public <T> ApiResponse<T> error(String message, List<ApiResponse.ApiError> errors) {
    return ApiResponse.error(message, errors);
  }

  public <T> ApiResponse<T> error(String message) {
    return ApiResponse.error(message, List.of());
  }

  public <T> ApiResponse<T> validationError(
      String message, String field, String code, String errorMessage) {
    return ApiResponse.error(message, field, code, errorMessage);
  }

  public <T> ApiResponse<T> notFound(String resourceName) {
    return error(
        resourceName + " not found",
        List.of(
            ApiResponse.ApiError.builder()
                .field("id")
                .message(resourceName + " not found")
                .code("NOT_FOUND")
                .build()));
  }

  public <T> ApiResponse<T> unauthorized(String message) {
    return error(
        message,
        List.of(ApiResponse.ApiError.builder().message(message).code("UNAUTHORIZED").build()));
  }

  public <T> ApiResponse<T> forbidden(String message) {
    return error(
        message,
        List.of(ApiResponse.ApiError.builder().message(message).code("FORBIDDEN").build()));
  }

  public <T> ApiResponse<T> conflict(String message, String field, String code) {
    return error(
        message,
        List.of(ApiResponse.ApiError.builder().field(field).message(message).code(code).build()));
  }
}
