package io.tonyblack10.aiplayground.rag.service;

import io.tonyblack10.aiplayground.config.rag.vectorstore.RedisVectorStoreProperties;
import io.tonyblack10.aiplayground.rag.model.VectorStoreInfo;
import io.tonyblack10.aiplayground.rag.registry.DocumentRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class VectorStoreRegistry {

  private final Map<String, VectorStore> stores;
  private final Map<String, String> redisDisplayNames;
  private final DocumentRegistry documentRegistry;

  public VectorStoreRegistry(
      @Qualifier("simpleVectorStore") VectorStore simpleVectorStore,
      @Qualifier("pgVectorStore") VectorStore pgVectorStore,
      @Qualifier("redisVectorStores") Map<String, VectorStore> redisVectorStores,
      RedisVectorStoreProperties redisVectorStoreProperties,
      DocumentRegistry documentRegistry) {
    this.documentRegistry = documentRegistry;
    this.stores = new LinkedHashMap<>();
    this.stores.put("simpleVectorStore", simpleVectorStore);
    this.stores.put("pgVectorStore", pgVectorStore);
    this.stores.putAll(redisVectorStores);

    this.redisDisplayNames = new LinkedHashMap<>();
    for (RedisVectorStoreProperties.StoreConfig storeConfig : redisVectorStoreProperties.getStores()) {
      if (StringUtils.hasText(storeConfig.getDisplayName())) {
        this.redisDisplayNames.put(storeConfig.getName(), storeConfig.getDisplayName());
      }
    }
  }

  public List<VectorStoreInfo> getAllStoreInfos() {
    return stores.entrySet().stream()
        .map(e -> toInfo(e.getKey(), e.getValue()))
        .toList();
  }

  public VectorStoreInfo getStoreInfo(String storeId) {
    VectorStore store = stores.get(storeId);
    if (store == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown store: " + storeId);
    return toInfo(storeId, store);
  }

  public VectorStore getStore(String storeId) {
    VectorStore store = stores.get(storeId);
    if (store == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown store: " + storeId);
    return store;
  }

  private VectorStoreInfo toInfo(String id, VectorStore store) {
    String type = resolveType(store);
    String displayName = resolveDisplayName(id);
    boolean primary = "simpleVectorStore".equals(id);
    int count = documentRegistry.count(id);
    return new VectorStoreInfo(id, displayName, type, primary, count);
  }

  private String resolveType(VectorStore store) {
    if (store instanceof SimpleVectorStore) return "SIMPLE";
    String name = store.getClass().getSimpleName();
    if (name.contains("Pg") || name.contains("Postgres")) return "PGVECTOR";
    if (name.contains("Redis")) return "REDIS";
    return name.toUpperCase();
  }

  private String resolveDisplayName(String storeId) {
    return switch (storeId) {
      case "simpleVectorStore" -> "Simple (In-Memory)";
      case "pgVectorStore" -> "PgVector (PostgreSQL)";
      default -> redisDisplayNames.getOrDefault(storeId, storeId);
    };
  }
}
