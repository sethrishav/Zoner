package com.zoner;

import static org.assertj.core.api.Assertions.assertThat;

import com.zoner.common.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Boots the full app against a real PostgreSQL (Testcontainers) and checks the M0 "done when" items. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ApplicationSmokeTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayBaselineWasApplied() {
        String value = jdbc.queryForObject(
                "SELECT value FROM app_metadata WHERE key = 'schema_baseline'", String.class);
        assertThat(value).isEqualTo("M0");
    }

    @Test
    void healthEndpointIsUp() {
        ResponseEntity<Map> response = rest.getForEntity("/healthz", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void livenessEndpointIsUpWithoutTouchingTheDatabase() {
        ResponseEntity<Map> response = rest.getForEntity("/healthz/liveness", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void openApiDocumentIsServed() {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Zoner API");
    }

    @Test
    void swaggerUiIsAccessible() {
        ResponseEntity<String> response = rest.getForEntity("/swagger-ui.html", String.class);
        // Springdoc redirects /swagger-ui.html to /swagger-ui/index.html or responds 200/302
        assertThat(response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is3xxRedirection()).isTrue();
    }

    @Test
    void unknownRouteReturnsTheStandardErrorShapeWithTraceId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", "smoke-test-0001");
        ResponseEntity<Map> response = rest.exchange(
                "/api/does-not-exist", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("smoke-test-0001");
        assertThat(response.getBody())
                .containsEntry("code", "NOT_FOUND")
                .containsEntry("traceId", "smoke-test-0001")
                .containsKeys("status", "message", "timestamp");
    }
}
