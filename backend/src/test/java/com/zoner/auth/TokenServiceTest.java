package com.zoner.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private PersonalAccessTokenRepository patRepository;
    private UserRepository userRepository;
    private Clock clock;
    private TokenService tokenService;

    private final Instant now = Instant.parse("2026-10-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        patRepository = mock(PersonalAccessTokenRepository.class);
        userRepository = mock(UserRepository.class);
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        tokenService = new TokenService(patRepository, userRepository, clock);
    }

    @Test
    @DisplayName("createToken generates zoner_pat_ token, stores SHA-256 hash, returns plaintext once")
    void testCreateToken() {
        User user = new User("alice@example.com", "hash", "Alice", "UTC");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(patRepository.save(any(PersonalAccessToken.class))).thenAnswer(invocation -> {
            PersonalAccessToken saved = invocation.getArgument(0);
            return saved;
        });

        var res = tokenService.createToken(1L, new TokenDto.CreateTokenRequest("Cursor IDE", "calendar:read,calendar:write", 30));

        assertThat(res.token()).startsWith("zoner_pat_");
        assertThat(res.tokenPrefix()).startsWith("zoner_pat_");
        assertThat(res.tokenPrefix()).hasSize(14);
        assertThat(res.name()).isEqualTo("Cursor IDE");
        assertThat(res.expiresAt()).isEqualTo(now.plusSeconds(30L * 86400));
        verify(patRepository).save(any(PersonalAccessToken.class));
    }

    @Test
    @DisplayName("authenticateToken authenticates valid PAT and updates last_used_at")
    void testAuthenticateTokenValid() {
        User user = new User("alice@example.com", "hash", "Alice", "UTC");
        String rawToken = "zoner_pat_11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff";
        String hash = JwtService.sha256Hex(rawToken);

        PersonalAccessToken pat = new PersonalAccessToken(user, "Test Token", "zoner_pat_1122...", hash, "calendar:read", null);
        when(patRepository.findByTokenHashWithUser(hash)).thenReturn(Optional.of(pat));

        User authenticated = tokenService.authenticateToken(rawToken);

        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getEmail()).isEqualTo("alice@example.com");
        assertThat(pat.getLastUsedAt()).isEqualTo(now);
        verify(patRepository).save(pat);
    }

    @Test
    @DisplayName("authenticateToken rejects revoked PAT")
    void testAuthenticateTokenRevoked() {
        User user = new User("alice@example.com", "hash", "Alice", "UTC");
        String rawToken = "zoner_pat_revokedtoken1234567890abcdef1234567890abcdef1234567890abcdef";
        String hash = JwtService.sha256Hex(rawToken);

        PersonalAccessToken pat = new PersonalAccessToken(user, "Revoked Token", "zoner_pat_revo...", hash, "calendar:read", null);
        pat.revoke();
        when(patRepository.findByTokenHashWithUser(hash)).thenReturn(Optional.of(pat));

        User authenticated = tokenService.authenticateToken(rawToken);
        assertThat(authenticated).isNull();
    }

    @Test
    @DisplayName("authenticateToken rejects expired PAT")
    void testAuthenticateTokenExpired() {
        User user = new User("alice@example.com", "hash", "Alice", "UTC");
        String rawToken = "zoner_pat_expiredtoken1234567890abcdef1234567890abcdef1234567890abcdef";
        String hash = JwtService.sha256Hex(rawToken);

        Instant expiredInPast = now.minusSeconds(3600);
        PersonalAccessToken pat = new PersonalAccessToken(user, "Expired Token", "zoner_pat_expi...", hash, "calendar:read", expiredInPast);
        when(patRepository.findByTokenHashWithUser(hash)).thenReturn(Optional.of(pat));

        User authenticated = tokenService.authenticateToken(rawToken);
        assertThat(authenticated).isNull();
    }
}
