package com.inmobiliaria.visit_calendar_service.exception;

import com.inmobiliaria.visit_calendar_service.dto.response.ApiResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.ResponseFactory;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final ResponseFactory responseFactory;

  @ExceptionHandler(ScheduleConflictException.class)
  public ResponseEntity<ApiResponse<Void>> handleScheduleConflict(ScheduleConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(responseFactory.conflict(ex.getMessage(), "schedule", "SCHEDULE_CONFLICT"));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(responseFactory.notFound(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    List<ApiResponse.ApiError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fieldError ->
                    ApiResponse.ApiError.builder()
                        .field(fieldError.getField())
                        .message(fieldError.getDefaultMessage())
                        .code("VALIDATION_ERROR")
                        .build())
            .collect(Collectors.toList());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(responseFactory.error("Validation failed", errors));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(responseFactory.error(ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(responseFactory.error("An unexpected error occurred: " + ex.getMessage()));
  }
}
