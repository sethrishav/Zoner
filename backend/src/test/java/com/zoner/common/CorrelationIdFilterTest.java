package com.zoner.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.zoner.common.logging.CorrelationIdFilter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesAnIdWhenNoneIsSent() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/ping");
        var response = new MockHttpServletResponse();
        var seenInChain = new AtomicReference<String>();

        filter.doFilter(request, response, (req, res) -> seenInChain.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(seenInChain.get()).isNotBlank();
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(seenInChain.get());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).as("MDC cleaned up").isNull();
    }

    @Test
    void reusesAValidIncomingId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/ping");
        request.addHeader(CorrelationIdFilter.HEADER, "client-req-12345");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("client-req-12345");
    }

    @Test
    void replacesAnUnsafeIncomingId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/ping");
        request.addHeader(CorrelationIdFilter.HEADER, "bad id with spaces <script>");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .isNotEqualTo("bad id with spaces <script>")
                .matches("^[A-Za-z0-9._-]{8,64}$");
    }
}
