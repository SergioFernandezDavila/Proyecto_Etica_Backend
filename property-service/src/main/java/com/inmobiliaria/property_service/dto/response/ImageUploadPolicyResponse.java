package com.inmobiliaria.property_service.dto.response;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadPolicyResponse {
  private String url;
  private String objectKey;
  private Map<String, String> formData;
  private int expiresInSeconds;
}
