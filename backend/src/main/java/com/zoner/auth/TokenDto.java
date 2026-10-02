package com.zoner.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public class TokenDto {

    public record CreateTokenRequest(
            @NotBlank(message = "Token name is required.")
            @Size(max = 100, message = "Token name cannot exceed 100 characters.")
            String name,

            String scopes,

            Integer expiresInDays
    ) {}

    public record CreateTokenResponse(
            Long id,
            String name,
            String token,
            String tokenPrefix,
            String scopes,
            Instant createdAt,
            Instant expiresAt
    ) {}

    public record TokenResponse(
            Long id,
            String name,
            String tokenPrefix,
            String scopes,
            Instant lastUsedAt,
            Instant expiresAt,
            boolean revoked,
            Instant createdAt
    ) {
        public static TokenResponse fromEntity(PersonalAccessToken pat) {
            return new TokenResponse(
                    pat.getId(),
                    pat.getName(),
                    pat.getTokenPrefix(),
                    pat.getScopes(),
                    pat.getLastUsedAt(),
                    pat.getExpiresAt(),
                    pat.isRevoked(),
                    pat.getCreatedAt()
            );
        }
    }
}
