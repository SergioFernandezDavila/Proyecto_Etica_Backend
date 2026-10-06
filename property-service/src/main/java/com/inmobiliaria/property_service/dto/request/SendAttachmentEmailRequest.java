package com.inmobiliaria.property_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SendAttachmentEmailRequest(
    @NotBlank String recipientId,
    @NotBlank String subject,
    @NotBlank String content,
    @NotBlank String attachmentBase64,
    @NotBlank String attachmentFilename) {}
