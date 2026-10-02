package com.zoner.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.zoner.auth.AuthDto.AuthResponse;
import com.zoner.auth.AuthDto.LoginRequest;
import com.zoner.auth.AuthDto.RefreshTokenRequest;
import com.zoner.auth.AuthDto.RegisterRequest;
import com.zoner.auth.AuthDto.UpdateProfileRequest;
import com.zoner.auth.AuthDto.UserProfileResponse;
import com.zoner.common.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("Registration succeeds with valid payload, returning 201 Created and tokens")
    void registrationSucceeds() {
        RegisterRequest request = new RegisterRequest(
                "sarah@example.com", "securePass123", "Sarah Connor", "America/New_York");

        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.refreshToken()).isNotBlank();
        assertThat(body.user().email()).isEqualTo("sarah@example.com");
        assertThat(body.user().displayName()).isEqualTo("Sarah Connor");
        assertThat(body.user().timeZone()).isEqualTo("America/New_York");
    }

    @Test
    @DisplayName("Registration rejects duplicate email with 409 Conflict")
    void duplicateEmailReturnsConflict() {
        RegisterRequest request = new RegisterRequest(
                "duplicate@example.com", "password123", "User One", "UTC");
        rest.postForEntity("/api/auth/register", request, AuthResponse.class);

        // Attempt second registration with same email (different case)
        RegisterRequest duplicate = new RegisterRequest(
                "DUPLICATE@example.com", "differentPassword", "User Two", "UTC");
        ResponseEntity<Map> response = rest.postForEntity(
                "/api/auth/register", duplicate, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "CONFLICT");
    }

    @Test
    @DisplayName("Registration rejects invalid email and short password with 400 Bad Request")
    void validationErrorsReturnBadRequest() {
        RegisterRequest invalid = new RegisterRequest(
                "not-an-email", "short", "", "UTC");

        ResponseEntity<Map> response = rest.postForEntity(
                "/api/auth/register", invalid, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("Registration rejects invalid IANA time zone identifier with 422 Unprocessable Entity")
    void invalidTimeZoneReturnsBadRequest() {
        RegisterRequest invalidTz = new RegisterRequest(
                "timetraveler@example.com", "password123", "Doc Brown", "Mars/Olympus");

        ResponseEntity<Map> response = rest.postForEntity(
                "/api/auth/register", invalidTz, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("code", "BUSINESS_RULE_VIOLATION");
    }

    @Test
    @DisplayName("Login succeeds with valid credentials, returning 200 OK and tokens")
    void loginSucceedsWithValidCredentials() {
        String email = "login.test@example.com";
        String password = "secretPassword123";
        rest.postForEntity("/api/auth/register",
                new RegisterRequest(email, password, "Login Tester", "Asia/Kolkata"),
                AuthResponse.class);

        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest(email, password), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().user().email()).isEqualTo(email);
    }

    @Test
    @DisplayName("Login fails with bad password returning 401 Unauthorized")
    void loginFailsWithBadPassword() {
        String email = "wrongpass@example.com";
        rest.postForEntity("/api/auth/register",
                new RegisterRequest(email, "correctPassword", "Test User", "UTC"),
                AuthResponse.class);

        ResponseEntity<Map> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest(email, "incorrectPassword"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("code", "UNAUTHENTICATED");
    }

    @Test
    @DisplayName("Token refresh rotates refresh token and returns a new access token")
    void refreshTokenRotationWorks() {
        RegisterRequest request = new RegisterRequest(
                "rotator@example.com", "password123", "Rotator", "UTC");
        AuthResponse initial = rest.postForEntity(
                "/api/auth/register", request, AuthResponse.class).getBody();
        assertThat(initial).isNotNull();

        ResponseEntity<AuthResponse> refreshResponse = rest.postForEntity(
                "/api/auth/refresh",
                new RefreshTokenRequest(initial.refreshToken()),
                AuthResponse.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse refreshed = refreshResponse.getBody();
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotEqualTo(initial.refreshToken());

        // The old refresh token must now be revoked
        ResponseEntity<Map> reuseAttempt = rest.postForEntity(
                "/api/auth/refresh",
                new RefreshTokenRequest(initial.refreshToken()),
                Map.class);
        assertThat(reuseAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Logout revokes the refresh token")
    void logoutRevokesToken() {
        RegisterRequest request = new RegisterRequest(
                "logout.user@example.com", "password123", "Logout User", "UTC");
        AuthResponse auth = rest.postForEntity(
                "/api/auth/register", request, AuthResponse.class).getBody();
        assertThat(auth).isNotNull();

        ResponseEntity<Void> logoutResponse = rest.postForEntity(
                "/api/auth/logout",
                new RefreshTokenRequest(auth.refreshToken()),
                Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Attempting to refresh should now fail
        ResponseEntity<Map> refreshAttempt = rest.postForEntity(
                "/api/auth/refresh",
                new RefreshTokenRequest(auth.refreshToken()),
                Map.class);
        assertThat(refreshAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Protected endpoint /api/me rejects unauthenticated request with 401 and standard error shape")
    void unauthenticatedAccessToMeReturns401() {
        ResponseEntity<Map> response = rest.getForEntity("/api/me", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody())
                .containsEntry("code", "UNAUTHENTICATED")
                .containsKeys("status", "message", "timestamp");
    }

    @Test
    @DisplayName("Protected endpoint /api/me returns user profile and allows PATCH update")
    void authenticatedMeEndpointAndProfileUpdate() {
        RegisterRequest register = new RegisterRequest(
                "profile.tester@example.com", "password123", "Initial Name", "Europe/London");
        AuthResponse auth = rest.postForEntity("/api/auth/register", register, AuthResponse.class).getBody();
        assertThat(auth).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(auth.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        // GET /api/me
        ResponseEntity<UserProfileResponse> getMeResponse = rest.exchange(
                "/api/me", HttpMethod.GET, new HttpEntity<>(headers), UserProfileResponse.class);
        assertThat(getMeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getMeResponse.getBody()).isNotNull();
        assertThat(getMeResponse.getBody().displayName()).isEqualTo("Initial Name");
        assertThat(getMeResponse.getBody().timeZone()).isEqualTo("Europe/London");

        // PATCH /api/me
        UpdateProfileRequest updateRequest = new UpdateProfileRequest("Updated Name", "Asia/Tokyo");
        ResponseEntity<UserProfileResponse> patchResponse = rest.exchange(
                "/api/me", HttpMethod.PATCH, new HttpEntity<>(updateRequest, headers), UserProfileResponse.class);

        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResponse.getBody()).isNotNull();
        assertThat(patchResponse.getBody().displayName()).isEqualTo("Updated Name");
        assertThat(patchResponse.getBody().timeZone()).isEqualTo("Asia/Tokyo");
    }
}
