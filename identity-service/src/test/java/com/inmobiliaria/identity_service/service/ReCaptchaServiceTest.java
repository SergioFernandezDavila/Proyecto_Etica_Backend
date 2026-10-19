package com.inmobiliaria.identity_service.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.inmobiliaria.identity_service.exception.UnauthorizedException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class ReCaptchaServiceTest {

  @Mock private RestTemplate restTemplate;

  @InjectMocks private ReCaptchaService reCaptchaService;

  private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
  private static final String SECRET_KEY = "test-secret-key";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(reCaptchaService, "secretKey", SECRET_KEY);
    ReflectionTestUtils.setField(reCaptchaService, "enabled", true);
  }

  @Test
  @DisplayName("Should pass without calling Google when disabled")
  void verify_whenDisabled_shouldDoNothing() {
    ReflectionTestUtils.setField(reCaptchaService, "enabled", false);

    assertThatCode(() -> reCaptchaService.verify("some-token")).doesNotThrowAnyException();
    verifyNoInteractions(restTemplate);
  }

  @Test
  @DisplayName("Should pass without calling Google for dev-bypass token")
  void verify_whenDevBypass_shouldDoNothing() {
    assertThatCode(() -> reCaptchaService.verify("dev-bypass")).doesNotThrowAnyException();
    verifyNoInteractions(restTemplate);
  }

  @Test
  @DisplayName("Should throw UnauthorizedException when token is null")
  void verify_whenTokenIsNull_shouldThrowUnauthorizedException() {
    assertThatThrownBy(() -> reCaptchaService.verify(null))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("reCAPTCHA token is required");
    verifyNoInteractions(restTemplate);
  }

  @Test
  @DisplayName("Should throw UnauthorizedException when token is blank")
  void verify_whenTokenIsBlank_shouldThrowUnauthorizedException() {
    assertThatThrownBy(() -> reCaptchaService.verify("   "))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("reCAPTCHA token is required");
    verifyNoInteractions(restTemplate);
  }

  @Test
  @DisplayName("Should pass when Google returns success true")
  void verify_whenGoogleReturnsSuccess_shouldPass() {
    Map<String, Object> googleResponse = Map.of("success", true, "hostname", "localhost");
    when(restTemplate.postForObject(eq(VERIFY_URL), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(googleResponse);

    assertThatCode(() -> reCaptchaService.verify("valid-token")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Should throw UnauthorizedException when Google returns success false")
  void verify_whenGoogleReturnsFailure_shouldThrowUnauthorizedException() {
    Map<String, Object> googleResponse =
        Map.of("success", false, "error-codes", List.of("invalid-input-response"));
    when(restTemplate.postForObject(eq(VERIFY_URL), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(googleResponse);

    assertThatThrownBy(() -> reCaptchaService.verify("invalid-token"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("invalid-input-response");
  }

  @Test
  @DisplayName("Should throw UnauthorizedException when Google returns null response")
  void verify_whenGoogleReturnsNull_shouldThrowUnauthorizedException() {
    when(restTemplate.postForObject(eq(VERIFY_URL), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(null);

    assertThatThrownBy(() -> reCaptchaService.verify("valid-token"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("reCAPTCHA verification failed");
  }

  @Test
  @DisplayName("Should throw UnauthorizedException when RestTemplate fails")
  void verify_whenRestTemplateThrows_shouldThrowUnauthorizedException() {
    when(restTemplate.postForObject(eq(VERIFY_URL), any(HttpEntity.class), eq(Map.class)))
        .thenThrow(new ResourceAccessException("Connection timeout"));

    assertThatThrownBy(() -> reCaptchaService.verify("valid-token"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("reCAPTCHA service unavailable");
  }
}
