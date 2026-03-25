package io.tonyblack10.aiplayground.chat.service;

import io.tonyblack10.aiplayground.chat.model.ProviderInfo;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProviderRegistry {

  private static final List<ProviderInfo> PROVIDERS = List.of(
      new ProviderInfo("openai", "OpenAI", List.of("gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo"))
  );

  public List<ProviderInfo> getAllProviders() {
    return PROVIDERS;
  }

  public ProviderInfo getProvider(String id) {
    return PROVIDERS.stream()
        .filter(p -> p.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + id));
  }

  public List<String> getModels(String providerId) {
    return getProvider(providerId).models();
  }
}
