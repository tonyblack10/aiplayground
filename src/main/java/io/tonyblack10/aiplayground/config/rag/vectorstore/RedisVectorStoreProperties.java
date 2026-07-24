package io.tonyblack10.aiplayground.config.rag.vectorstore;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds Redis vector store configuration from application configuration.
 *
 * <p>All Redis vector stores share the same underlying Redis connection
 * ({@code host}/{@code port}/credentials). Each entry under {@code stores} is
 * registered as its own vector store, keyed by its unique {@code name}, so
 * that any number of Redis-backed indexes can coexist alongside the other
 * vector store backends.
 *
 * <p>Example configuration:
 * <pre>
 * app:
 *   rag:
 *     redis:
 *       host: localhost
 *       port: 6379
 *       stores:
 *         - name: redisVectorStore
 *           display-name: Redis
 *           index-name: custom-index
 *           prefix: "custom-prefix"
 *           vector-algorithm: HNSW
 *           initialize-schema: true
 * </pre>
 */
@ConfigurationProperties(prefix = "app.rag.redis")
public class RedisVectorStoreProperties {

  private String host = "localhost";
  private int port = 6379;
  private String username;
  private String password;
  private boolean ssl = false;
  private List<StoreConfig> stores = new ArrayList<>();

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public boolean isSsl() {
    return ssl;
  }

  public void setSsl(boolean ssl) {
    this.ssl = ssl;
  }

  public List<StoreConfig> getStores() {
    return stores;
  }

  public void setStores(List<StoreConfig> stores) {
    this.stores = stores;
  }

  /** Configuration for a single Redis-backed vector store. */
  public static class StoreConfig {

    private String name;
    private String displayName;
    private String indexName;
    private String prefix;
    private String contentFieldName;
    private String embeddingFieldName;
    private String vectorAlgorithm = "HNSW";
    private boolean initializeSchema = true;
    private List<MetadataFieldConfig> metadataFields = new ArrayList<>();

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }

    public String getIndexName() {
      return indexName;
    }

    public void setIndexName(String indexName) {
      this.indexName = indexName;
    }

    public String getPrefix() {
      return prefix;
    }

    public void setPrefix(String prefix) {
      this.prefix = prefix;
    }

    public String getContentFieldName() {
      return contentFieldName;
    }

    public void setContentFieldName(String contentFieldName) {
      this.contentFieldName = contentFieldName;
    }

    public String getEmbeddingFieldName() {
      return embeddingFieldName;
    }

    public void setEmbeddingFieldName(String embeddingFieldName) {
      this.embeddingFieldName = embeddingFieldName;
    }

    public String getVectorAlgorithm() {
      return vectorAlgorithm;
    }

    public void setVectorAlgorithm(String vectorAlgorithm) {
      this.vectorAlgorithm = vectorAlgorithm;
    }

    public boolean isInitializeSchema() {
      return initializeSchema;
    }

    public void setInitializeSchema(boolean initializeSchema) {
      this.initializeSchema = initializeSchema;
    }

    public List<MetadataFieldConfig> getMetadataFields() {
      return metadataFields;
    }

    public void setMetadataFields(List<MetadataFieldConfig> metadataFields) {
      this.metadataFields = metadataFields;
    }
  }

  /** Configuration for a single metadata field to index for filtering (TEXT, TAG, or NUMERIC). */
  public static class MetadataFieldConfig {

    private String name;
    private String type = "TEXT";

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }
  }
}
