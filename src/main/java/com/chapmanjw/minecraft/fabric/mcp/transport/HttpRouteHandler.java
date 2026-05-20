package com.chapmanjw.minecraft.fabric.mcp.transport;

/**
 * Handles one HTTP route. Implementations are registered with the
 * {@link HttpTransport} at startup; the transport handles security filtering,
 * size limits, and rate limiting before delegating here.
 *
 * <p>Streaming responses (SSE) are implemented via {@link StreamingHttpRouteHandler}.
 */
@FunctionalInterface
public interface HttpRouteHandler {

    HttpResponse handle(HttpRequest request) throws Exception;
}
