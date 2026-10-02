package com.zoner.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

public class McpProtocol {

    public static final String PROTOCOL_VERSION = "2024-11-05";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record McpRequest(
            String jsonrpc,
            Object id,
            String method,
            Map<String, Object> params
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record McpResponse(
            String jsonrpc,
            Object id,
            Object result,
            McpError error
    ) {
        public static McpResponse success(Object id, Object result) {
            return new McpResponse("2.0", id, result, null);
        }

        public static McpResponse error(Object id, int code, String message) {
            return new McpResponse("2.0", id, null, new McpError(code, message, null));
        }

        public static McpResponse error(Object id, int code, String message, Object data) {
            return new McpResponse("2.0", id, null, new McpError(code, message, data));
        }
    }

    public record McpError(
            int code,
            String message,
            Object data
    ) {}

    public record McpTool(
            String name,
            String description,
            Map<String, Object> inputSchema
    ) {}

    public record McpContent(
            String type,
            String text
    ) {
        public static McpContent text(String text) {
            return new McpContent("text", text);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record McpToolResult(
            List<McpContent> content,
            Boolean isError
    ) {
        public static McpToolResult success(String text) {
            return new McpToolResult(List.of(McpContent.text(text)), false);
        }

        public static McpToolResult error(String text) {
            return new McpToolResult(List.of(McpContent.text(text)), true);
        }
    }

    public record McpInitializeResult(
            String protocolVersion,
            Map<String, Object> capabilities,
            Map<String, Object> serverInfo
    ) {}
}
