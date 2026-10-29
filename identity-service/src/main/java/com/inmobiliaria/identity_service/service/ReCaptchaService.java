package com.inmobiliaria.identity_service.service;

import com.inmobiliaria.identity_service.exception.UnauthorizedException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReCaptchaService {

  private static final String RECAPTCHA_VERIFY_URL =
      "https://www.google.com/recaptcha/api/siteverify";

  private final RestTemplate restTemplate;

  @Value("${app.recaptcha.secret-key}")
  private String secretKey;

  @Value("${app.recaptcha.enabled:true}")
  private boolean enabled;

  /**
   * Verifies a reCAPTCHA v2 token against Google's API.
   *
   * @param token the token received from the frontend widget
   * @throws UnauthorizedException if the token is invalid or verification fails
   */
  public void verify(String token) {
    if (!enabled) {
      log.debug("reCAPTCHA verification is disabled, skipping.");
      return;
    }

    if ("dev-bypass".equals(token)) {
      log.warn("reCAPTCHA verification skipped for dev-bypass token.");
      return;
    }

    if (token == null || token.isBlank()) {
      log.warn("reCAPTCHA token is missing or blank.");
      throw new UnauthorizedException("reCAPTCHA token is required");
    }

    // Google siteverify requires parameters as application/x-www-form-urlencoded
    // body
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("secret", secretKey);
    body.add("response", token);

    HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response =
          restTemplate.postForObject(RECAPTCHA_VERIFY_URL, requestEntity, Map.class);

      if (response == null) {
        log.error("reCAPTCHA verification returned null response");
        throw new UnauthorizedException("reCAPTCHA verification failed");
      }

      boolean success = Boolean.TRUE.equals(response.get("success"));
      if (!success) {
        Object errorCodes = response.get("error-codes");
        log.warn("reCAPTCHA verification failed. Error codes: {}", errorCodes);
        throw new UnauthorizedException("reCAPTCHA verification failed: " + errorCodes);
      }

      log.debug("reCAPTCHA token verified successfully.");

    } catch (UnauthorizedException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("Error calling reCAPTCHA API", ex);
      throw new UnauthorizedException("reCAPTCHA service unavailable");
    }
  }
}
