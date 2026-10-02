package com.zoner.auth;

import com.zoner.common.error.NotFoundException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    public static final String PAT_PREFIX = "zoner_pat_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PersonalAccessTokenRepository patRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public TokenService(
            PersonalAccessTokenRepository patRepository,
            UserRepository userRepository,
            Clock clock) {
        this.patRepository = patRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public TokenDto.CreateTokenResponse createToken(Long userId, TokenDto.CreateTokenRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Generate 32 bytes of secure random entropy -> 64 hex characters
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String randomHex = HexFormat.of().formatHex(randomBytes);
        String plaintextToken = PAT_PREFIX + randomHex;

        // Prefix for safe display in UI: e.g. "zoner_pat_a1b2" (14 chars, fits in VARCHAR(16) and VARCHAR(32))
        String displayPrefix = PAT_PREFIX + randomHex.substring(0, 4);
        String tokenHash = JwtService.sha256Hex(plaintextToken);

        Instant expiresAt = null;
        if (request.expiresInDays() != null && request.expiresInDays() > 0) {
            expiresAt = clock.instant().plus(request.expiresInDays(), ChronoUnit.DAYS);
        }

        String scopes = (request.scopes() != null && !request.scopes().isBlank())
                ? request.scopes().trim()
                : "calendar:read,calendar:write";

        PersonalAccessToken pat = new PersonalAccessToken(
                user,
                request.name().trim(),
                displayPrefix,
                tokenHash,
                scopes,
                expiresAt
        );

        PersonalAccessToken saved = patRepository.save(pat);

        return new TokenDto.CreateTokenResponse(
                saved.getId(),
                saved.getName(),
                plaintextToken,
                saved.getTokenPrefix(),
                saved.getScopes(),
                saved.getCreatedAt(),
                saved.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public List<TokenDto.TokenResponse> listTokens(Long userId) {
        return patRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(TokenDto.TokenResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void revokeToken(Long userId, Long tokenId) {
        PersonalAccessToken pat = patRepository.findByIdAndUserId(tokenId, userId)
                .orElseThrow(() -> new NotFoundException("Personal access token not found"));
        pat.revoke();
        patRepository.save(pat);
    }

    /**
     * Authenticates a raw personal access token, records usage, and returns the User.
     * Returns null if token is invalid, revoked, or expired.
     */
    @Transactional
    public User authenticateToken(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(PAT_PREFIX)) {
            return null;
        }

        String hash = JwtService.sha256Hex(rawToken.trim());
        PersonalAccessToken pat = patRepository.findByTokenHashWithUser(hash).orElse(null);

        if (pat == null || !pat.isValid(clock.instant())) {
            return null;
        }

        pat.recordUsage(clock.instant());
        patRepository.save(pat);
        return pat.getUser();
    }
}
