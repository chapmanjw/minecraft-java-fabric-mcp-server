package com.chapmanjw.minecraft.fabric.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 envelope types used by the MCP wire protocol.
 *
 * <p>The MCP spec mandates JSON-RPC 2.0 framing. We keep these as plain Java records
 * so Jackson handles serialization/deserialization without reflection magic.
 */
public final class JsonRpc {

    private JsonRpc() {}

    /** Inbound request from the client. {@code id} is null for notifications. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
            @JsonProperty("jsonrpc") String jsonrpc,
            @JsonProperty("id") JsonNode id,
            @JsonProperty("method") String method,
            @JsonProperty("params") JsonNode params) {}

    /** Outbound success response. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SuccessResponse(
            @JsonProperty("jsonrpc") String jsonrpc,
            @JsonProperty("id") JsonNode id,
            @JsonProperty("result") JsonNode result) {

        public static SuccessResponse of(JsonNode id, JsonNode result) {
            return new SuccessResponse("2.0", id, result);
        }
    }

    /** Outbound error response. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(
            @JsonProperty("jsonrpc") String jsonrpc,
            @JsonProperty("id") JsonNode id,
            @JsonProperty("error") ErrorBody error) {

        public static ErrorResponse of(JsonNode id, int code, String message, JsonNode data) {
            return new ErrorResponse("2.0", id, new ErrorBody(code, message, data));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(
            @JsonProperty("code") int code,
            @JsonProperty("message") String message,
            @JsonProperty("data") JsonNode data) {}

    /** Server-initiated notification (no id). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Notification(
            @JsonProperty("jsonrpc") String jsonrpc,
            @JsonProperty("method") String method,
            @JsonProperty("params") JsonNode params) {

        public static Notification of(String method, JsonNode params) {
            return new Notification("2.0", method, params);
        }
    }
}
