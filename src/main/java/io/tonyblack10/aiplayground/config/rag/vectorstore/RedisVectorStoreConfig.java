package io.tonyblack10.aiplayground.config.rag.vectorstore;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
public class RedisVectorStoreConfig {

  @Bean
  public JedisPooled jedisPooled() {
    return new JedisPooled("localhost", 6379);
  }

  @Bean("redisVectorStore")
  public VectorStore redisVectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
    return RedisVectorStore.builder(jedisPooled, embeddingModel)
        .indexName("custom-index")
        .prefix("custom-prefix")
        .initializeSchema(true)
        .batchingStrategy(new TokenCountBatchingStrategy())
        .build();
  }

}
