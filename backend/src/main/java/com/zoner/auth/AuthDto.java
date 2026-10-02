package com.zoner.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Consolidated DTOs for authentication, token rotation, and user profile management.
 */
public final class AuthDto {

    private AuthDto() {}

    public record RegisterRequest(
            @Schema(description = "User's email address", example = "alice@example.com")
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,

            @Schema(description = "Password (minimum 8 characters)", example = "password123")
            @NotBlank(message = "Password is required")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String password,

            @Schema(description = "Display name", example = "Alice Smith")
            @NotBlank(message = "Display name is required")
            @Size(max = 100, message = "Display name must not exceed 100 characters")
            String displayName,

            @Schema(description = "IANA Time zone identifier (default is UTC)", example = "Asia/Kolkata")
            String timeZone
    ) {}

    public record LoginRequest(
            @Schema(description = "User's email address", example = "alice@example.com")
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,

            @Schema(description = "Password", example = "password123")
            @NotBlank(message = "Password is required")
            String password
    ) {}

    public record RefreshTokenRequest(
            @Schema(description = "Refresh token string")
            @NotBlank(message = "Refresh token is required")
            String refreshToken
    ) {}

    public record UserSummary(
            Long id,
            String email,
            String displayName,
            String timeZone
    ) {
        public static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getTimeZone());
        }
    }

    public record AuthResponse(
            @Schema(description = "JWT Access Token (valid for 15 minutes)")
            String accessToken,

            @Schema(description = "Token type", example = "Bearer")
            String tokenType,

            @Schema(description = "Expiration duration in seconds", example = "900")
            long expiresIn,

            @Schema(description = "Opaque Refresh Token")
            String refreshToken,

            @Schema(description = "Authenticated user profile")
            UserSummary user
    ) {
        public static AuthResponse of(String accessToken, long expiresIn, String refreshToken, UserSummary user) {
            return new AuthResponse(accessToken, "Bearer", expiresIn, refreshToken, user);
        }
    }

    public record UserProfileResponse(
            Long id,
            String email,
            String displayName,
            String timeZone,
            Instant createdAt
    ) {
        public static UserProfileResponse from(User user) {
            return new UserProfileResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getTimeZone(),
                    user.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(
            @Schema(description = "Updated display name", example = "Alice Walker")
            @Size(max = 100, message = "Display name must not exceed 100 characters")
            String displayName,

            @Schema(description = "Updated IANA Time zone identifier", example = "America/New_York")
            String timeZone
    ) {}
}
