package com.zoner.auth;

import com.zoner.auth.AuthDto.AuthResponse;
import com.zoner.auth.AuthDto.LoginRequest;
import com.zoner.auth.AuthDto.RegisterRequest;
import com.zoner.auth.AuthDto.UpdateProfileRequest;
import com.zoner.auth.AuthDto.UserProfileResponse;
import com.zoner.auth.AuthDto.UserSummary;
import com.zoner.common.error.BusinessRuleException;
import com.zoner.common.error.ConflictException;
import com.zoner.common.error.NotFoundException;
import com.zoner.common.error.UnauthenticatedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public record UserRegisteredEvent(User user) {}

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("An account with that email address already exists.");
        }

        String validTimeZone = validateAndNormalizeTimeZone(request.timeZone());

        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                validTimeZone);
        user = userRepository.save(user);

        // Notify domain listeners (e.g. provision default calendars in M2)
        eventPublisher.publishEvent(new UserRegisteredEvent(user));

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UnauthenticatedException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthenticatedException("Invalid email or password.");
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthenticatedException("Refresh token is required.");
        }

        String hash = JwtService.sha256Hex(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashWithUser(hash)
                .orElseThrow(() -> new UnauthenticatedException("Invalid or expired refresh token."));

        Instant now = clock.instant();
        if (!refreshToken.isActive(now)) {
            throw new UnauthenticatedException("Refresh token is expired or has been revoked.");
        }

        // Revoke the used refresh token (token rotation)
        refreshToken.revoke(now);
        refreshTokenRepository.save(refreshToken);

        User user = refreshToken.getUser();
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String hash = JwtService.sha256Hex(rawRefreshToken);
            refreshTokenRepository.findByTokenHashWithUser(hash).ifPresent(rt -> {
                rt.revoke(clock.instant());
                refreshTokenRepository.save(rt);
            });
        }
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateMe(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }

        if (request.timeZone() != null && !request.timeZone().isBlank()) {
            String validZone = validateAndNormalizeTimeZone(request.timeZone());
            user.setTimeZone(validZone);
        }

        user = userRepository.save(user);
        return UserProfileResponse.from(user);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = JwtService.generateRandomToken();
        String tokenHash = JwtService.sha256Hex(rawRefreshToken);

        Instant expiresAt = clock.instant().plus(REFRESH_TOKEN_VALIDITY);
        RefreshToken refreshToken = new RefreshToken(user, tokenHash, expiresAt);
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(
                accessToken,
                JwtService.ACCESS_TOKEN_EXPIRATION.toSeconds(),
                rawRefreshToken,
                UserSummary.from(user));
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String validateAndNormalizeTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return "UTC";
        }
        try {
            return ZoneId.of(timeZone.trim()).getId();
        } catch (Exception e) {
            throw new BusinessRuleException("Invalid IANA time zone identifier: '" + timeZone + "'");
        }
    }
}
