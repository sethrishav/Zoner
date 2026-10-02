package com.zoner.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.zoner.auth.AuthDto.AuthResponse;
import com.zoner.auth.AuthDto.RegisterRequest;
import com.zoner.calendar.CalendarDto.CalendarResponse;
import com.zoner.calendar.CalendarDto.CalendarShareResponse;
import com.zoner.calendar.CalendarDto.CreateCalendarRequest;
import com.zoner.calendar.CalendarDto.ShareCalendarRequest;
import com.zoner.calendar.CalendarDto.UpdateCalendarRequest;
import com.zoner.calendar.CalendarDto.UpdatePreferenceRequest;
import com.zoner.common.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
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
class CalendarIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private AuthResponse registerUser(String email, String name) {
        RegisterRequest req = new RegisterRequest(email, "password123", name, "UTC");
        return rest.postForEntity("/api/auth/register", req, AuthResponse.class).getBody();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("User registration automatically provisions a default Personal calendar")
    void defaultCalendarIsProvisionedOnRegistration() {
        AuthResponse user = registerUser("default.cal@example.com", "Default Tester");

        ResponseEntity<List<CalendarResponse>> response = rest.exchange(
                "/api/calendars",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(user.accessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<CalendarResponse> calendars = response.getBody();
        assertThat(calendars).isNotNull().hasSize(1);
        CalendarResponse cal = calendars.get(0);
        assertThat(cal.name()).isEqualTo("Personal");
        assertThat(cal.isDefault()).isTrue();
        assertThat(cal.permission()).isEqualTo(SharePermission.OWNER);
        assertThat(cal.enabled()).isTrue();
    }

    @Test
    @DisplayName("User can create a custom calendar and duplicate names are rejected")
    void createCalendarAndDuplicateRejection() {
        AuthResponse user = registerUser("creator@example.com", "Calendar Creator");
        HttpHeaders headers = authHeaders(user.accessToken());

        CreateCalendarRequest req = new CreateCalendarRequest("Work", "Company projects", "#10B981");
        ResponseEntity<CalendarResponse> created = rest.exchange(
                "/api/calendars", HttpMethod.POST, new HttpEntity<>(req, headers), CalendarResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().name()).isEqualTo("Work");
        assertThat(created.getBody().color()).isEqualTo("#10B981");
        assertThat(created.getBody().isDefault()).isFalse();

        // Duplicate name rejection
        ResponseEntity<Map> dupResponse = rest.exchange(
                "/api/calendars", HttpMethod.POST, new HttpEntity<>(req, headers), Map.class);
        assertThat(dupResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dupResponse.getBody()).containsEntry("code", "CONFLICT");
    }

    @Test
    @DisplayName("Default calendar cannot be deleted")
    void defaultCalendarCannotBeDeleted() {
        AuthResponse user = registerUser("nodelete@example.com", "No Delete");
        HttpHeaders headers = authHeaders(user.accessToken());

        List<CalendarResponse> calendars = rest.exchange(
                "/api/calendars", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<CalendarResponse>>() {}).getBody();
        assertThat(calendars).isNotNull();
        Long defaultId = calendars.get(0).id();

        ResponseEntity<Map> deleteResp = rest.exchange(
                "/api/calendars/" + defaultId, HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(deleteResp.getBody()).containsEntry("code", "BUSINESS_RULE_VIOLATION");
    }

    @Test
    @DisplayName("AccessPolicy prevents stranger from accessing or modifying another user's calendar (returns 404)")
    void accessPolicyPreventsStrangerAccessWithoutLeakingExistence() {
        AuthResponse alice = registerUser("alice.access@example.com", "Alice");
        AuthResponse bob = registerUser("bob.access@example.com", "Bob");

        // Alice creates a calendar
        CreateCalendarRequest req = new CreateCalendarRequest("Secret Roadmap", "Confidential", "#EF4444");
        CalendarResponse secretCal = rest.exchange(
                "/api/calendars", HttpMethod.POST, new HttpEntity<>(req, authHeaders(alice.accessToken())), CalendarResponse.class
        ).getBody();
        assertThat(secretCal).isNotNull();

        // Bob tries to GET Alice's calendar -> 404 (not 403, preventing ID guessing)
        ResponseEntity<Map> getResp = rest.exchange(
                "/api/calendars/" + secretCal.id(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(bob.accessToken())), Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Bob tries to DELETE Alice's calendar -> 404
        ResponseEntity<Map> delResp = rest.exchange(
                "/api/calendars/" + secretCal.id(), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(bob.accessToken())), Map.class);
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Sharing lifecycle: share with VIEW/EDIT, visibility in shared list, permission checks, and unshare")
    void sharingLifecycleAndPermissionsMatrix() {
        AuthResponse owner = registerUser("owner.share@example.com", "Owner");
        AuthResponse viewer = registerUser("viewer.share@example.com", "Viewer");
        HttpHeaders ownerHeaders = authHeaders(owner.accessToken());
        HttpHeaders viewerHeaders = authHeaders(viewer.accessToken());

        // 1. Owner creates calendar
        CalendarResponse teamCal = rest.exchange(
                "/api/calendars", HttpMethod.POST,
                new HttpEntity<>(new CreateCalendarRequest("Team Sprint", "Sprint events", "#8B5CF6"), ownerHeaders),
                CalendarResponse.class).getBody();
        assertThat(teamCal).isNotNull();

        // 2. Owner shares with Viewer with VIEW permission
        ShareCalendarRequest shareReq = new ShareCalendarRequest(viewer.user().email(), SharePermission.VIEW);
        ResponseEntity<CalendarShareResponse> shareResp = rest.exchange(
                "/api/calendars/" + teamCal.id() + "/shares", HttpMethod.POST,
                new HttpEntity<>(shareReq, ownerHeaders), CalendarShareResponse.class);
        assertThat(shareResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(shareResp.getBody()).isNotNull();
        assertThat(shareResp.getBody().permission()).isEqualTo(SharePermission.VIEW);

        // 3. Viewer sees shared calendar in their list with VIEW permission
        List<CalendarResponse> viewerCalendars = rest.exchange(
                "/api/calendars", HttpMethod.GET, new HttpEntity<>(viewerHeaders),
                new ParameterizedTypeReference<List<CalendarResponse>>() {}).getBody();
        assertThat(viewerCalendars).isNotNull().hasSize(2); // Personal + Team Sprint
        CalendarResponse sharedInList = viewerCalendars.stream()
                .filter(c -> c.id().equals(teamCal.id()))
                .findFirst().orElseThrow();
        assertThat(sharedInList.permission()).isEqualTo(SharePermission.VIEW);
        assertThat(sharedInList.owner().email()).isEqualTo(owner.user().email());

        // 4. Viewer cannot rename or delete calendar (403 Forbidden)
        UpdateCalendarRequest renameReq = new UpdateCalendarRequest("Hacked Sprint", "Hacked", "#000000");
        ResponseEntity<Map> renameResp = rest.exchange(
                "/api/calendars/" + teamCal.id(), HttpMethod.PUT,
                new HttpEntity<>(renameReq, viewerHeaders), Map.class);
        assertThat(renameResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 5. Viewer cannot manage shares (403 Forbidden)
        ResponseEntity<Map> manageSharesResp = rest.exchange(
                "/api/calendars/" + teamCal.id() + "/shares", HttpMethod.GET,
                new HttpEntity<>(viewerHeaders), Map.class);
        assertThat(manageSharesResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 6. Viewer updates their preference to disable visibility and override color
        UpdatePreferenceRequest prefReq = new UpdatePreferenceRequest(false, "#F59E0B");
        ResponseEntity<CalendarResponse> prefResp = rest.exchange(
                "/api/calendars/" + teamCal.id() + "/preference", HttpMethod.PATCH,
                new HttpEntity<>(prefReq, viewerHeaders), CalendarResponse.class);
        assertThat(prefResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(prefResp.getBody()).isNotNull();
        assertThat(prefResp.getBody().enabled()).isFalse();
        assertThat(prefResp.getBody().color()).isEqualTo("#F59E0B");

        // 7. Owner upgrades Viewer to EDIT permission
        ShareCalendarRequest upgradeReq = new ShareCalendarRequest(viewer.user().email(), SharePermission.EDIT);
        ResponseEntity<CalendarShareResponse> upgradeResp = rest.exchange(
                "/api/calendars/" + teamCal.id() + "/shares", HttpMethod.POST,
                new HttpEntity<>(upgradeReq, ownerHeaders), CalendarShareResponse.class);
        assertThat(upgradeResp.getBody().permission()).isEqualTo(SharePermission.EDIT);

        // 8. Viewer can unshare themselves
        ResponseEntity<Void> unshareResp = rest.exchange(
                "/api/calendars/" + teamCal.id() + "/shares/" + viewer.user().id(),
                HttpMethod.DELETE, new HttpEntity<>(viewerHeaders), Void.class);
        assertThat(unshareResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Calendar is no longer in Viewer's list
        List<CalendarResponse> finalViewerList = rest.exchange(
                "/api/calendars", HttpMethod.GET, new HttpEntity<>(viewerHeaders),
                new ParameterizedTypeReference<List<CalendarResponse>>() {}).getBody();
        assertThat(finalViewerList).hasSize(1);
    }
}

