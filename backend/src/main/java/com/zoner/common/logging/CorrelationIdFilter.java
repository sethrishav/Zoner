package com.zoner.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a traceId: reuses a sane incoming X-Request-Id or generates one, puts it in
 * the logging MDC (so every log line carries it), and echoes it in the response header and in
 * error bodies. Support can then find the exact logs for a user's failed request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    // Only accept simple ids: prevents log injection through the header.
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String traceId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (!request.getRequestURI().startsWith("/healthz")) {
                log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            }
            MDC.remove(MDC_KEY);
        }
    }
}
