package io.tonyblack10.aiplayground.chat.model;

import java.util.List;

public record ChatSettings(
    String provider,
    String model,
    double temperature,
    String ragStoreId,
    List<String> tools
) {

  public static ChatSettings defaults() {
    return new ChatSettings("openai", "gpt-4o-mini", 0.7, null, List.of());
  }

  public boolean hasRag() {
    return ragStoreId != null && !ragStoreId.isBlank() && !"none".equals(ragStoreId);
  }

  public boolean hasTool(String toolName) {
    return tools != null && tools.contains(toolName);
  }
}
