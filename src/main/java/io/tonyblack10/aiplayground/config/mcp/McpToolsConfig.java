package io.tonyblack10.aiplayground.config.mcp;

import io.tonyblack10.aiplayground.rag.mcp.RagSearchMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolsConfig {

  @Bean
  public ToolCallbackProvider ragSearchTools(RagSearchMcpTools ragSearchMcpTools) {
    return MethodToolCallbackProvider.builder()
        .toolObjects(ragSearchMcpTools)
        .build();
  }
}
