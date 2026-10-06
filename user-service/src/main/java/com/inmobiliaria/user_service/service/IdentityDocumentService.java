package com.inmobiliaria.user_service.service;

import com.inmobiliaria.user_service.domain.DocumentType;
import com.inmobiliaria.user_service.domain.IdentityDocument;
import com.inmobiliaria.user_service.exception.ResourceAlreadyExistsException;
import com.inmobiliaria.user_service.repository.IdentityDocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class IdentityDocumentService {

  private final IdentityDocumentRepository repository;
  private final MinioStorageService storageService;

  public IdentityDocument uploadDocument(
      String personId, DocumentType documentType, MultipartFile file) {

    // Enforce one per type: Throw error if it already exists
    boolean exists =
        repository.findByPersonId(personId).stream()
            .anyMatch(doc -> doc.getDocumentType() == documentType);

    if (exists) {
      throw new ResourceAlreadyExistsException(
          "A document of type "
              + documentType
              + " already exists for this person. Please delete it before uploading a new one.");
    }

    String fileKey = storageService.uploadFile(file, personId);
    String currentUserId =
        (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    IdentityDocument document =
        IdentityDocument.builder()
            .personId(personId)
            .documentType(documentType)
            .fileUrl(fileKey)
            .uploadDate(Instant.now())
            .uploadedById(currentUserId)
            .build();

    document.setCreatedAt(Instant.now());
    document.setCreatedBy(currentUserId);

    return repository.save(document);
  }

  public List<IdentityDocument> getDocumentsByPersonId(String personId) {
    List<IdentityDocument> documents = repository.findByPersonId(personId);
    documents.forEach(doc -> doc.setFileUrl(storageService.generatePresignedUrl(doc.getFileUrl())));
    return documents;
  }

  public void deleteDocument(String personId, String documentId) {
    Optional<IdentityDocument> docOpt = repository.findById(documentId);

    if (docOpt.isPresent()) {
      IdentityDocument doc = docOpt.get();
      if (doc.getPersonId().equals(personId)) {
        storageService.deleteFile(doc.getFileUrl());
        repository.delete(doc);
      } else {
        throw new IllegalArgumentException("Document does not belong to the specified person.");
      }
    }
  }
}
