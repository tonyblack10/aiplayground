package io.tonyblack10.aiplayground.config.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.tonyblack10.aiplayground.chat.service.tools.UserToolContext;
import io.tonyblack10.aiplayground.rag.mcp.RagSearchMcpTools;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class McpToolsConfig {

    @Bean
    public List<AsyncToolSpecification> ragSearchToolSpecifications(
            RagSearchMcpTools ragSearchMcpTools,
            @Qualifier("mcpServerJsonMapper") JsonMapper mcpServerJsonMapper) {
        return Arrays.stream(
                MethodToolCallbackProvider.builder()
                    .toolObjects(ragSearchMcpTools)
                    .build()
                    .getToolCallbacks()
            )
            .map(toolCallback -> {
                var tool = McpSchema.Tool.builder()
                    .name(toolCallback.getToolDefinition().name())
                    .description(toolCallback.getToolDefinition().description())
                    .inputSchema(mcpServerJsonMapper.readValue(
                        toolCallback.getToolDefinition().inputSchema(),
                        McpSchema.JsonSchema.class))
                    .build();

                return new AsyncToolSpecification(tool,
                    (transportCtx, request) -> ReactiveSecurityContextHolder.getContext()
                        .mapNotNull(ctx -> UserToolContext.from(ctx.getAuthentication()))
                        .flatMap(userCtx -> invoke(toolCallback, transportCtx, request, userCtx, mcpServerJsonMapper))
                        .switchIfEmpty(Mono.defer(
                            () -> invoke(toolCallback, transportCtx, request, null, mcpServerJsonMapper))));
            })
            .toList();
    }

    private static Mono<McpSchema.CallToolResult> invoke(
            ToolCallback toolCallback,
            McpTransportContext transportCtx,
            McpSchema.CallToolRequest request,
            UserToolContext userCtx,
            JsonMapper mcpServerJsonMapper) {

        return Mono.fromCallable(() -> {
            Map<String, Object> ctxMap = new HashMap<>();
            ctxMap.put(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, transportCtx);
            if (userCtx != null) {
                ctxMap.put(UserToolContext.TOOL_CONTEXT_KEY, userCtx);
            }
            try {
                String result = toolCallback.call(
                    mcpServerJsonMapper.writeValueAsString(request.arguments()),
                    new ToolContext(ctxMap));
                return McpSchema.CallToolResult.builder().addTextContent(result).isError(false).build();
            } catch (Exception e) {
                return McpSchema.CallToolResult.builder().addTextContent(e.getMessage()).isError(true).build();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
