package com.inmobiliaria.user_service.controller;

import com.inmobiliaria.user_service.domain.DocumentType;
import com.inmobiliaria.user_service.domain.IdentityDocument;
import com.inmobiliaria.user_service.dto.response.ApiResponse;
import com.inmobiliaria.user_service.dto.response.ResponseFactory;
import com.inmobiliaria.user_service.service.IdentityDocumentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for handling identity documents of persons. (Future Swagger Documentation will be
 * added here)
 */
@RestController
@RequestMapping("/persons/{id}/documents")
@RequiredArgsConstructor
public class IdentityDocumentController {

  private final IdentityDocumentService service;
  private final ResponseFactory responseFactory;

  /** Uploads an identity document for a person. Allowed types: PDF, JPG, PNG. Max size: 10MB. */
  @PostMapping
  public ResponseEntity<ApiResponse<IdentityDocument>> uploadDocument(
      @PathVariable("id") String personId,
      @RequestParam("documentType") DocumentType documentType,
      @RequestParam("file") MultipartFile file) {

    IdentityDocument document = service.uploadDocument(personId, documentType, file);
    return ResponseEntity.status(201)
        .body(responseFactory.created("Document uploaded successfully", document));
  }

  /** Retrieves all identity documents for a person with temporary access URLs. */
  @GetMapping
  public ResponseEntity<ApiResponse<List<IdentityDocument>>> getDocuments(
      @PathVariable("id") String personId) {
    List<IdentityDocument> documents = service.getDocumentsByPersonId(personId);
    return ResponseEntity.ok(
        responseFactory.success("Documents retrieved successfully", documents));
  }

  /** Deletes an identity document. */
  @DeleteMapping("/{documentId}")
  public ResponseEntity<ApiResponse<Void>> deleteDocument(
      @PathVariable("id") String personId, @PathVariable("documentId") String documentId) {
    service.deleteDocument(personId, documentId);
    return ResponseEntity.status(204)
        .body(responseFactory.deleted("Document deleted successfully"));
  }
}
