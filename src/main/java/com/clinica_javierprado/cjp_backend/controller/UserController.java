package com.clinica_javierprado.cjp_backend.controller;

import com.clinica_javierprado.cjp_backend.domain.User;
import com.clinica_javierprado.cjp_backend.dto.EditProfileRequest;
import com.clinica_javierprado.cjp_backend.dto.UserProfileResponse;
import com.clinica_javierprado.cjp_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getProfile(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestPart("data") @Valid EditProfileRequest request,
            @RequestPart(value = "photo", required = false) MultipartFile profilePhoto) {
        // Keep accepting `photo` for API compatibility, but file storage is disabled.
        return ResponseEntity.ok(userService.updateProfile(user, request));
    }

    // @GetMapping("/doctors-and-branches.")
    // public ResponseEntity<List<String>> getMedicalSpecialties() {
    // return ResponseEntity.ok(userService.getMedicalSpecialties());
    // }
}
