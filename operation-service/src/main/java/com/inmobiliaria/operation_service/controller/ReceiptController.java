package com.inmobiliaria.operation_service.controller;

import com.inmobiliaria.operation_service.dto.ReceiptResponse;
import com.inmobiliaria.operation_service.dto.ReceiptUploadRequest;
import com.inmobiliaria.operation_service.dto.response.ApiResponse;
import com.inmobiliaria.operation_service.dto.response.ResponseFactory;
import com.inmobiliaria.operation_service.service.ReceiptService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** REST controller for payment receipt management. */
@Slf4j
@RestController
@RequestMapping("/operations/{operationId}/receipts")
public class ReceiptController {

  private final ReceiptService receiptService;
  private final ResponseFactory responseFactory;

  public ReceiptController(ReceiptService receiptService, ResponseFactory responseFactory) {
    this.receiptService = receiptService;
    this.responseFactory = responseFactory;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<ReceiptResponse>> attachReceipt(
      @PathVariable String operationId,
      @RequestHeader("X-Auth-User-Id") String userId,
      @RequestHeader("X-Auth-Roles") String roles,
      @RequestParam("file") MultipartFile file,
      @RequestParam("amount") String amount,
      @RequestParam("currency") String currency,
      @RequestParam("paymentDate") String paymentDate,
      @RequestParam("concept") String concept) {

    try {
      ReceiptUploadRequest request = new ReceiptUploadRequest();
      request.setAmount(new java.math.BigDecimal(amount));
      request.setCurrency(currency);

      // Handle ISO-8601 strings (like from JS toISOString()) which may have 'Z' or offset
      java.time.LocalDateTime parsedDate;
      if (paymentDate.contains("Z") || paymentDate.contains("+")) {
        parsedDate = java.time.OffsetDateTime.parse(paymentDate).toLocalDateTime();
      } else {
        parsedDate = java.time.LocalDateTime.parse(paymentDate);
      }
      request.setPaymentDate(parsedDate);
      request.setConcept(concept);

      ReceiptResponse response =
          receiptService.attachReceipt(operationId, userId, roles, file, request);
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(responseFactory.created("Receipt attached successfully", response));

    } catch (java.time.format.DateTimeParseException e) {
      log.warn("[ReceiptController] Invalid date format: {}", paymentDate);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(responseFactory.error("Invalid date format. Expected ISO-8601."));
    } catch (com.inmobiliaria.operation_service.exception.ResourceNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(responseFactory.error(e.getMessage()));
    } catch (com.inmobiliaria.operation_service.exception.ValidationException e) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(responseFactory.error(e.getMessage()));
    } catch (IllegalArgumentException e) {
      log.warn("[ReceiptController] Invalid file upload attempt: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
          .body(responseFactory.error(e.getMessage()));
    } catch (Exception e) {
      log.error("[ReceiptController] Failed to attach receipt", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(responseFactory.error("Failed to upload receipt. Please try again."));
    }
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<ReceiptResponse>>> listReceipts(
      @PathVariable String operationId) {

    List<ReceiptResponse> receipts = receiptService.listReceipts(operationId);
    return ResponseEntity.ok(responseFactory.success("Receipts retrieved successfully", receipts));
  }

  @DeleteMapping("/{receiptId}")
  public ResponseEntity<ApiResponse<Void>> deleteReceipt(
      @PathVariable String operationId,
      @PathVariable String receiptId,
      @RequestHeader("X-Auth-User-Id") String userId,
      @RequestHeader("X-Auth-Roles") String roles) {

    try {
      receiptService.deleteReceipt(operationId, receiptId, userId, roles);
      return ResponseEntity.ok(responseFactory.deleted("Receipt deleted successfully."));

    } catch (com.inmobiliaria.operation_service.exception.ValidationException e) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(responseFactory.error(e.getMessage()));
    } catch (RuntimeException e) {
      log.warn("[ReceiptController] Delete failed: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(responseFactory.error(e.getMessage()));
    }
  }
}
