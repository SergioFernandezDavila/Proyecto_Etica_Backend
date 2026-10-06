package com.inmobiliaria.property_service.controller;

import com.inmobiliaria.property_service.dto.request.ConfirmUploadRequest;
import com.inmobiliaria.property_service.dto.request.GenerateUploadUrlRequest;
import com.inmobiliaria.property_service.dto.request.UpdateDocumentPermissionsRequest;
import com.inmobiliaria.property_service.dto.response.ApiResponse;
import com.inmobiliaria.property_service.dto.response.DocumentResponse;
import com.inmobiliaria.property_service.dto.response.ResponseFactory;
import com.inmobiliaria.property_service.service.DocumentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

  private final DocumentService documentService;
  private final ResponseFactory responseFactory;

  /**
   * US1: Generate presigned URL for uploading a document Validates file type and size before
   * issuing the URL
   */
  @PostMapping("/upload-url")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Map<String, String>>> generateUploadUrl(
      @Valid @RequestBody GenerateUploadUrlRequest request) {
    log.info(
        "Generating upload URL for property: {}, type: {}, file: {}",
        request.getPropertyId(),
        request.getDocumentType(),
        request.getFileName());
    Map<String, String> data = documentService.generatePresignedUploadUrl(request);
    return ResponseEntity.ok(responseFactory.success("Upload URL generated", data));
  }

  /** US1: Confirm successful upload and register document in MongoDB. */
  @PostMapping("/confirm")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<DocumentResponse>> confirmUpload(
      @Valid @RequestBody ConfirmUploadRequest request) {
    log.info(
        "Confirming upload for property: {}, document: {}",
        request.getPropertyId(),
        request.getObjectKey());
    DocumentResponse data = documentService.confirmUpload(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(responseFactory.created("Document registered successfully", data));
  }

  /**
   * US1 & US2: Get all documents for a property with temporary download URLs Permission check
   * before generating any presigned GET URL
   */
  @GetMapping("/property/{propertyId}")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<ApiResponse<List<DocumentResponse>>> getPropertyDocuments(
      @PathVariable String propertyId) {
    log.info("Fetching documents for property: {}", propertyId);
    List<DocumentResponse> data = documentService.getPropertyDocuments(propertyId);
    return ResponseEntity.ok(responseFactory.success("Documents retrieved", data));
  }

  /**
   * US1 & US2: Get a specific document with temporary download URL Permission check before
   * generating presigned GET URL
   */
  @GetMapping("/{documentId}")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(
      @PathVariable String documentId) {
    log.info("Fetching document: {}", documentId);
    DocumentResponse data = documentService.getDocument(documentId);
    return ResponseEntity.ok(responseFactory.success("Document retrieved", data));
  }

  /** US2: Update document access permissions (Admin only) */
  @PatchMapping("/{documentId}/permissions")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<DocumentResponse>> updateDocumentPermissions(
      @PathVariable String documentId,
      @Valid @RequestBody UpdateDocumentPermissionsRequest request) {
    log.info("Updating permissions for document: {}", documentId);
    DocumentResponse data =
        documentService.updateDocumentPermissions(documentId, request.getAccessPolicy());
    return ResponseEntity.ok(responseFactory.success("Document permissions updated", data));
  }

  /**
   * US2: Generate a new temporary download URL for an existing document Useful when previous URL
   * expired
   */
  @PostMapping("/{documentId}/refresh-url")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<ApiResponse<Map<String, String>>> refreshDownloadUrl(
      @PathVariable String documentId) {
    log.info("Refreshing download URL for document: {}", documentId);
    String url = documentService.generateTemporaryDownloadUrl(documentId);
    Map<String, String> data =
        Map.of(
            "temporaryDownloadUrl",
            url,
            "expiresInSeconds",
            String.valueOf(documentService.getPresignedExpirySeconds()));
    return ResponseEntity.ok(responseFactory.success("Download URL refreshed", data));
  }

  /** Delete a document (Admin only) */
  @DeleteMapping("/{documentId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable String documentId) {
    log.info("Deleting document: {}", documentId);
    documentService.deleteDocument(documentId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT)
        .body(responseFactory.deleted("Document deleted successfully"));
  }
}
