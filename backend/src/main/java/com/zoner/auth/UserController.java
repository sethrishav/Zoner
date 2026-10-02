package com.zoner.auth;

import com.zoner.auth.AuthDto.UpdateProfileRequest;
import com.zoner.auth.AuthDto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "User Profile", description = "Current authenticated user profile management and time zone settings")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<UserProfileResponse> getMe(@CurrentUser UserPrincipal principal) {
        UserProfileResponse response = authService.getMe(principal.getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping
    @Operation(summary = "Update current user display name and/or time zone")
    public ResponseEntity<UserProfileResponse> updateMe(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserProfileResponse response = authService.updateMe(principal.getId(), request);
        return ResponseEntity.ok(response);
    }
}
