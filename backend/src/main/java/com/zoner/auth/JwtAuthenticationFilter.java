package com.zoner.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenService tokenService;

    public JwtAuthenticationFilter(JwtService jwtService, TokenService tokenService) {
        this.jwtService = jwtService;
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                if (token.startsWith(TokenService.PAT_PREFIX)) {
                    // Authenticate Personal Access Token (PAT) for MCP & external tools
                    User user = tokenService.authenticateToken(token);
                    if (user != null) {
                        UserPrincipal principal = new UserPrincipal(
                                user.getId(),
                                user.getEmail(),
                                "",
                                user.getDisplayName(),
                                user.getTimeZone()
                        );
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } else {
                    // Authenticate standard JWT session token
                    Claims claims = jwtService.parseAndValidateToken(token);
                    if (claims != null) {
                        Long userId = jwtService.extractUserId(claims);
                        String email = claims.get("email", String.class);
                        String displayName = claims.get("displayName", String.class);
                        String timeZone = claims.get("timeZone", String.class);

                        if (userId != null && email != null) {
                            UserPrincipal principal = new UserPrincipal(userId, email, "", displayName, timeZone);
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
