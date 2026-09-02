package io.tonyblack10.aiplayground.config.rag.vectorstore;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.RedisClient;

/**
 * Dynamically registers Redis-backed vector stores from {@code app.rag.redis} config.
 *
 * <p>All configured stores share a single Redis connection ({@link RedisClient}, Jedis 7's
 * unified client — replaces the pre-Jedis-7 {@code JedisPooled}); each entry under
 * {@code app.rag.redis.stores} becomes its own {@link RedisVectorStore}, keyed by its unique
 * {@code name} in the {@code redisVectorStores} map bean so it can be picked up by
 * {@link io.tonyblack10.aiplayground.rag.service.VectorStoreRegistry}.
 */
@Configuration
@EnableConfigurationProperties(RedisVectorStoreProperties.class)
public class RedisVectorStoreConfig {

  private static final Logger log = LoggerFactory.getLogger(RedisVectorStoreConfig.class);

  @Bean
  public RedisClient redisClient(RedisVectorStoreProperties properties) {
    HostAndPort hostAndPort = new HostAndPort(properties.getHost(), properties.getPort());
    DefaultJedisClientConfig.Builder configBuilder = DefaultJedisClientConfig.builder()
        .ssl(properties.isSsl());
    if (StringUtils.hasText(properties.getUsername())) {
      configBuilder.user(properties.getUsername());
    }
    if (StringUtils.hasText(properties.getPassword())) {
      configBuilder.password(properties.getPassword());
    }
    JedisClientConfig clientConfig = configBuilder.build();
    return RedisClient.builder()
        .hostAndPort(hostAndPort)
        .clientConfig(clientConfig)
        .build();
  }

  @Bean("redisVectorStores")
  public Map<String, VectorStore> redisVectorStores(
      RedisClient redisClient, EmbeddingModel embeddingModel, RedisVectorStoreProperties properties) {
    Map<String, VectorStore> stores = new LinkedHashMap<>();
    for (RedisVectorStoreProperties.StoreConfig storeConfig : properties.getStores()) {
      Assert.hasText(storeConfig.getName(), "Each entry under app.rag.redis.stores must have a unique 'name'");
      Assert.isTrue(!stores.containsKey(storeConfig.getName()),
          "Duplicate Redis vector store name: " + storeConfig.getName());
      log.info("Registering Redis vector store '{}' (index='{}', prefix='{}')",
          storeConfig.getName(), storeConfig.getIndexName(), storeConfig.getPrefix());
      stores.put(storeConfig.getName(), buildStore(redisClient, embeddingModel, storeConfig));
    }
    return stores;
  }

  private VectorStore buildStore(
      RedisClient redisClient, EmbeddingModel embeddingModel, RedisVectorStoreProperties.StoreConfig cfg) {
    RedisVectorStore.Builder builder = RedisVectorStore.builder(redisClient, embeddingModel)
        .indexName(cfg.getIndexName())
        .prefix(cfg.getPrefix())
        .contentFieldName(cfg.getContentFieldName())
        .embeddingFieldName(cfg.getEmbeddingFieldName())
        .initializeSchema(cfg.isInitializeSchema())
        .batchingStrategy(new TokenCountBatchingStrategy());

    if (StringUtils.hasText(cfg.getVectorAlgorithm())) {
      builder.vectorAlgorithm(RedisVectorStore.Algorithm.valueOf(cfg.getVectorAlgorithm().toUpperCase()));
    }
    if (!cfg.getMetadataFields().isEmpty()) {
      builder.metadataFields(cfg.getMetadataFields().stream().map(this::toMetadataField).toList());
    }
    RedisVectorStore store = builder.build();
    // Built manually rather than as its own @Bean, so Spring never invokes this
    // InitializingBean callback for us; without it the RediSearch index (FT.CREATE)
    // is never created and every search fails with "no such index".
    store.afterPropertiesSet();
    return store;
  }

  private RedisVectorStore.MetadataField toMetadataField(RedisVectorStoreProperties.MetadataFieldConfig field) {
    return switch (field.getType().toUpperCase()) {
      case "TAG" -> RedisVectorStore.MetadataField.tag(field.getName());
      case "NUMERIC" -> RedisVectorStore.MetadataField.numeric(field.getName());
      default -> RedisVectorStore.MetadataField.text(field.getName());
    };
  }

}
