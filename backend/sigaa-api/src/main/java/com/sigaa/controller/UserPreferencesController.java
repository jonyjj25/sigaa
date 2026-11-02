package com.sigaa.controller;

import com.sigaa.dto.UpdatePreferencesRequest;
import com.sigaa.dto.UserPreferencesResponse;
import com.sigaa.service.UserPreferencesService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/preferences")
public class UserPreferencesController {

    private final UserPreferencesService preferencesService;

    public UserPreferencesController(UserPreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserPreferencesResponse> getMyPreferences(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(preferencesService.getMyPreferences());
    }

    @PutMapping("/me")
    public ResponseEntity<UserPreferencesResponse> savePreferences(
            Authentication authentication,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(preferencesService.savePreferences(request));
    }

    @PostMapping("/me/reset")
    public ResponseEntity<UserPreferencesResponse> resetPreferences(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(preferencesService.resetPreferences());
    }
}
