package io.tonyblack10.aiplayground.config.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.http.HttpHeaders;

/** Shared access to the HTTP headers captured from the MCP transport layer. */
public final class McpTransportHeaders {

  /** Key under which the request's {@link HttpHeaders} are stored in the {@link McpTransportContext}. */
  public static final String HEADERS_CONTEXT_KEY = "httpHeaders";

  private McpTransportHeaders() {
  }

  /** Returns the HTTP headers of the originating MCP request, or empty headers if unavailable. */
  public static HttpHeaders from(ToolContext toolContext) {
    Object exchange = toolContext.getContext().get(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY);
    if (exchange instanceof McpTransportContext transportContext
        && transportContext.get(HEADERS_CONTEXT_KEY) instanceof HttpHeaders headers) {
      return headers;
    }
    return HttpHeaders.EMPTY;
  }
}
