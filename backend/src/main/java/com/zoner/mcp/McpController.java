package com.zoner.mcp;

import com.zoner.auth.CurrentUser;
import com.zoner.auth.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/mcp")
@Tag(name = "Model Context Protocol (MCP)", description = "MCP endpoints for AI assistants (Claude Desktop, Cursor, Claude Code)")
@SecurityRequirement(name = "bearerAuth")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final McpService mcpService;
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public McpController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    /**
     * Standard Streamable HTTP JSON-RPC 2.0 endpoint for MCP.
     */
    @PostMapping
    @Operation(summary = "MCP JSON-RPC Endpoint", description = "Executes MCP methods: initialize, tools/list, tools/call")
    public McpProtocol.McpResponse handleRpc(
            @CurrentUser UserPrincipal user,
            @RequestBody McpProtocol.McpRequest request) {

        if (request == null || request.method() == null) {
            return McpProtocol.McpResponse.error(null, -32600, "Invalid Request");
        }

        Object id = request.id();
        String method = request.method();
        Map<String, Object> params = request.params();

        log.debug("MCP method: {} from user: {}", method, user.getEmail());

        try {
            return switch (method) {
                case "initialize" -> {
                    McpProtocol.McpInitializeResult initResult = mcpService.handleInitialize(params);
                    yield McpProtocol.McpResponse.success(id, initResult);
                }
                case "notifications/initialized" -> {
                    // Client acknowledgement of initialization
                    yield McpProtocol.McpResponse.success(id, Map.of());
                }
                case "ping" -> McpProtocol.McpResponse.success(id, Map.of());
                case "tools/list" -> {
                    var tools = mcpService.listTools();
                    yield McpProtocol.McpResponse.success(id, Map.of("tools", tools));
                }
                case "tools/call" -> {
                    if (params == null || !params.containsKey("name")) {
                        yield McpProtocol.McpResponse.error(id, -32602, "Missing tool name in params");
                    }
                    String toolName = params.get("name").toString();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

                    McpProtocol.McpToolResult toolResult = mcpService.callTool(
                            user.getId(),
                            user.getTimeZone(),
                            toolName,
                            arguments
                    );
                    yield McpProtocol.McpResponse.success(id, toolResult);
                }
                default -> McpProtocol.McpResponse.error(id, -32601, "Method not found: " + method);
            };
        } catch (Exception e) {
            log.error("Error processing MCP method: {}", method, e);
            return McpProtocol.McpResponse.error(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * Server-Sent Events (SSE) transport for MCP (used by Claude Desktop via mcp-remote).
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "MCP SSE Transport", description = "Server-Sent Events endpoint for MCP streamable connections")
    public SseEmitter handleSse(@CurrentUser UserPrincipal user) {
        String sessionId = UUID.randomUUID().toString();
        // 30 minute timeout
        SseEmitter emitter = new SseEmitter(1800000L);

        activeEmitters.put(sessionId, emitter);

        emitter.onCompletion(() -> activeEmitters.remove(sessionId));
        emitter.onTimeout(() -> {
            emitter.complete();
            activeEmitters.remove(sessionId);
        });
        emitter.onError((e) -> activeEmitters.remove(sessionId));

        try {
            // MCP SSE specification: emit 'endpoint' event with POST target URI
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/api/mcp/message?sessionId=" + sessionId));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE endpoint event to user {}: {}", user.getEmail(), e.getMessage());
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * Message endpoint for active SSE sessions.
     */
    @PostMapping("/message")
    @Operation(summary = "MCP SSE Message Endpoint", description = "Processes messages routed to an active SSE session")
    public McpProtocol.McpResponse handleSseMessage(
            @CurrentUser UserPrincipal user,
            @RequestParam("sessionId") String sessionId,
            @RequestBody McpProtocol.McpRequest request) {
        // Execute the RPC request and return directly
        return handleRpc(user, request);
    }
}
