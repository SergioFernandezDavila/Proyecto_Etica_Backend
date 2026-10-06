package com.inmobiliaria.user_service.controller;

import com.inmobiliaria.user_service.domain.PersonPreferences;
import com.inmobiliaria.user_service.dto.response.ApiResponse;
import com.inmobiliaria.user_service.dto.response.ResponseFactory;
import com.inmobiliaria.user_service.exception.ValidationException;
import com.inmobiliaria.user_service.service.PersonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/buscadores", "/persons"})
@RequiredArgsConstructor
public class BuscadorController {

  private final PersonService personService;
  private final ResponseFactory responseFactory;

  @PostMapping("/{id}/preferencias")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<PersonPreferences>> savePrefs(
      @PathVariable String id, @Valid @RequestBody PersonPreferences prefs) {
    validatePrefs(prefs);
    PersonPreferences saved = personService.savePreferences(id, prefs);
    return ResponseEntity.ok(responseFactory.success("Preferencias registradas", saved));
  }

  @PutMapping("/{id}/preferencias")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<PersonPreferences>> updatePrefs(
      @PathVariable String id, @Valid @RequestBody PersonPreferences prefs) {
    validatePrefs(prefs);
    PersonPreferences saved = personService.savePreferences(id, prefs);
    return ResponseEntity.ok(responseFactory.success("Preferencias actualizadas", saved));
  }

  @GetMapping("/{id}/preferencias")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN') or hasRole('INTERESTED_CLIENT')")
  public ResponseEntity<ApiResponse<PersonPreferences>> getPrefs(@PathVariable String id) {
    PersonPreferences prefs = personService.getPreferences(id);
    return ResponseEntity.ok(responseFactory.success("Preferencias obtenidas", prefs));
  }

  private void validatePrefs(PersonPreferences prefs) {
    if (prefs.getMinRooms() != null
        && prefs.getMaxRooms() != null
        && prefs.getMinRooms() > prefs.getMaxRooms()) {
      throw new ValidationException("El mínimo de cuartos no puede ser mayor al máximo");
    }
  }
}
