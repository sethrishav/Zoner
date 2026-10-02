package com.zoner.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tokens")
@Tag(name = "Personal Access Tokens", description = "Endpoints for managing PATs used by MCP clients and integrations")
@SecurityRequirement(name = "bearerAuth")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a Personal Access Token", description = "Creates a PAT. The plaintext token is returned only once.")
    public TokenDto.CreateTokenResponse createToken(
            @CurrentUser UserPrincipal user,
            @Valid @RequestBody TokenDto.CreateTokenRequest request) {
        return tokenService.createToken(user.getId(), request);
    }

    @GetMapping
    @Operation(summary = "List Personal Access Tokens", description = "Lists all tokens created by the current user.")
    public List<TokenDto.TokenResponse> listTokens(@CurrentUser UserPrincipal user) {
        return tokenService.listTokens(user.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a Personal Access Token", description = "Revokes a token so it can no longer be used.")
    public void revokeToken(
            @CurrentUser UserPrincipal user,
            @PathVariable Long id) {
        tokenService.revokeToken(user.getId(), id);
    }
}
