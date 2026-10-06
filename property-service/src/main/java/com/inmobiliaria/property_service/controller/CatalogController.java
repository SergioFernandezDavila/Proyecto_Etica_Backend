package com.inmobiliaria.property_service.controller;

import com.inmobiliaria.property_service.domain.RetirementReason;
import com.inmobiliaria.property_service.dto.response.ApiResponse;
import com.inmobiliaria.property_service.dto.response.ResponseFactory;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalogos")
@RequiredArgsConstructor
public class CatalogController {

  private final ResponseFactory responseFactory;

  @GetMapping("/motivos-retiro")
  public ResponseEntity<ApiResponse<List<String>>> getRetirementReasons() {
    List<String> reasons = Arrays.stream(RetirementReason.values()).map(Enum::name).toList();
    return ResponseEntity.ok(responseFactory.success("Motivos de retiro obtenidos", reasons));
  }
}
