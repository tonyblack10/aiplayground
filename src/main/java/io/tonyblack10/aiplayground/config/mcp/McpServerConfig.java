package io.tonyblack10.aiplayground.config.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncCompletionSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncPromptSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncResourceSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncResourceTemplateSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.transport.WebFluxStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

/**
 * Replaces McpServerStatelessAutoConfiguration to fix a Spring AI 1.1.5 bug where
 * McpStatelessAsyncServer is built without calling .jsonMapper(), causing the server to
 * use a bare ObjectMapper (via SPI) that has FAIL_ON_UNKNOWN_PROPERTIES=true. This
 * causes -32603 errors when MCP clients send newer protocol fields (e.g. elicitation.form).
 */
@Configuration
@EnableConfigurationProperties({McpServerProperties.class, McpServerStreamableHttpProperties.class})
public class McpServerConfig {

    @Bean
    public McpSchema.ServerCapabilities.Builder capabilitiesBuilder() {
        return McpSchema.ServerCapabilities.builder();
    }

    /**
     * Overrides the auto-configured transport to capture the incoming HTTP headers into
     * the {@link McpTransportContext}, so tool implementations can read request headers
     * (e.g. to select a vector store) via {@link McpTransportHeaders}.
     */
    @Bean
    public WebFluxStatelessServerTransport webFluxStatelessServerTransport(
            @Qualifier("mcpServerObjectMapper") ObjectMapper mcpServerObjectMapper,
            McpServerStreamableHttpProperties streamableHttpProperties) {

        return WebFluxStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(mcpServerObjectMapper))
                .messageEndpoint(streamableHttpProperties.getMcpEndpoint())
                .contextExtractor(request -> McpTransportContext.create(
                        Map.of(McpTransportHeaders.HEADERS_CONTEXT_KEY, request.headers().asHttpHeaders())))
                .build();
    }

    @Bean
    public McpStatelessAsyncServer mcpStatelessAsyncServer(
            McpStatelessServerTransport statelessTransport,
            McpSchema.ServerCapabilities.Builder capabilitiesBuilder,
            McpServerProperties serverProperties,
            ObjectProvider<List<AsyncToolSpecification>> tools,
            ObjectProvider<List<AsyncResourceSpecification>> resources,
            ObjectProvider<List<AsyncResourceTemplateSpecification>> resourceTemplates,
            ObjectProvider<List<AsyncPromptSpecification>> prompts,
            ObjectProvider<List<AsyncCompletionSpecification>> completions,
            @Qualifier("mcpServerObjectMapper") ObjectMapper mcpServerObjectMapper) {

        McpSchema.Implementation serverInfo = new McpSchema.Implementation(
                serverProperties.getName(), serverProperties.getVersion());

        McpServer.StatelessAsyncSpecification serverBuilder = McpServer.async(statelessTransport)
                .serverInfo(serverInfo)
                .jsonMapper(new JacksonMcpJsonMapper(mcpServerObjectMapper));

        if (serverProperties.getCapabilities().isTool()) {
            List<AsyncToolSpecification> toolSpecs = new ArrayList<>(
                    tools.stream().flatMap(List::stream).toList());
            capabilitiesBuilder.tools(false);
            if (!CollectionUtils.isEmpty(toolSpecs)) {
                serverBuilder.tools(toolSpecs);
            }
        }

        if (serverProperties.getCapabilities().isResource()) {
            capabilitiesBuilder.resources(false, false);
            List<AsyncResourceSpecification> resourceSpecs = resources.stream().flatMap(List::stream).toList();
            if (!CollectionUtils.isEmpty(resourceSpecs)) {
                serverBuilder.resources(resourceSpecs);
            }
            List<AsyncResourceTemplateSpecification> templateSpecs =
                    resourceTemplates.stream().flatMap(List::stream).toList();
            if (!CollectionUtils.isEmpty(templateSpecs)) {
                serverBuilder.resourceTemplates(templateSpecs);
            }
        }

        if (serverProperties.getCapabilities().isPrompt()) {
            capabilitiesBuilder.prompts(false);
            List<AsyncPromptSpecification> promptSpecs = prompts.stream().flatMap(List::stream).toList();
            if (!CollectionUtils.isEmpty(promptSpecs)) {
                serverBuilder.prompts(promptSpecs);
            }
        }

        if (serverProperties.getCapabilities().isCompletion()) {
            capabilitiesBuilder.completions();
            List<AsyncCompletionSpecification> completionSpecs = completions.stream().flatMap(List::stream).toList();
            if (!CollectionUtils.isEmpty(completionSpecs)) {
                serverBuilder.completions(completionSpecs);
            }
        }

        serverBuilder.capabilities(capabilitiesBuilder.build());
        serverBuilder.instructions(serverProperties.getInstructions());
        serverBuilder.requestTimeout(serverProperties.getRequestTimeout());

        return serverBuilder.build();
    }
}
