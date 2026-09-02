package io.tonyblack10.aiplayground.config.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Overrides the auto-configured stateless WebFlux MCP transport solely to capture the
 * incoming HTTP headers into the {@link McpTransportContext}, so tool implementations can
 * read request headers (e.g. to select a vector store) via {@link McpTransportHeaders}.
 *
 * <p>Spring AI's {@code McpServerStatelessWebFluxAutoConfiguration} does not expose a way to
 * plug in a context extractor, so this bean still needs to be hand-built -- but (unlike the
 * Spring AI 1.1.5-era version of this class) it no longer needs to work around a missing
 * {@code .jsonMapper(...)} call: that bug is fixed upstream as of Spring AI 2.0 via the
 * {@code mcpServerJsonMapper} bean from {@code McpServerJsonMapperAutoConfiguration}, which
 * this bean reuses. The stateless async server itself is now built entirely by Spring AI's
 * own {@code McpServerStatelessAutoConfiguration} (no longer excluded in application.yaml).
 */
@Configuration
@EnableConfigurationProperties(McpServerStreamableHttpProperties.class)
public class McpServerConfig {

    @Bean
    public WebFluxStatelessServerTransport webFluxStatelessServerTransport(
            @Qualifier("mcpServerJsonMapper") JsonMapper mcpServerJsonMapper,
            McpServerStreamableHttpProperties streamableHttpProperties) {

        return WebFluxStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(mcpServerJsonMapper))
                .messageEndpoint(streamableHttpProperties.getMcpEndpoint())
                .contextExtractor(request -> McpTransportContext.create(
                        Map.of(McpTransportHeaders.HEADERS_CONTEXT_KEY, request.headers().asHttpHeaders())))
                .build();
    }
}
