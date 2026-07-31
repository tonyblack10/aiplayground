package io.tonyblack10.aiplayground.rag.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Batch-ingests {@code .json} files found (recursively, including subfolders) under
 * {@code app.rag.ingest.source-folder} into a Redis vector store.
 *
 * <p>Disabled by default so it never runs as a side effect of a normal {@code mvn
 * spring-boot:run}. Enable it with {@code app.rag.ingest.enabled=true} and point
 * {@code app.rag.ingest.source-folder} at the folder to import; optionally override
 * {@code app.rag.ingest.store-name} (defaults to {@code redisVectorStore}, the store id
 * configured under {@code app.rag.redis.stores}).
 *
 * <p>Each file must contain a {@code metadata} object and a markdown {@code content} string,
 * see {@link JsonDocumentPayload}. This runs once at startup, outside the reactive request
 * pipeline, so plain blocking file I/O is used here rather than scheduling on boundedElastic.
 */
@Component
public class JsonFolderIngestRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(JsonFolderIngestRunner.class);
  private static final int BATCH_SIZE = 50;

  private final Map<String, VectorStore> redisVectorStores;
  private final ObjectMapper objectMapper;
  private final boolean enabled;
  private final String sourceFolder;
  private final String storeName;

  public JsonFolderIngestRunner(
      @Qualifier("redisVectorStores") Map<String, VectorStore> redisVectorStores,
      ObjectMapper objectMapper,
      @Value("${app.rag.ingest.enabled:false}") boolean enabled,
      @Value("${app.rag.ingest.source-folder:}") String sourceFolder,
      @Value("${app.rag.ingest.store-name:redisVectorStore}") String storeName) {
    this.redisVectorStores = redisVectorStores;
    this.objectMapper = objectMapper;
    this.enabled = enabled;
    this.sourceFolder = sourceFolder;
    this.storeName = storeName;
  }

  @Override
  public void run(String... args) {
    if (!enabled) {
      return;
    }
    if (!StringUtils.hasText(sourceFolder)) {
      log.warn("app.rag.ingest.enabled=true but app.rag.ingest.source-folder is not set; skipping ingest");
      return;
    }
    VectorStore vectorStore = redisVectorStores.get(storeName);
    if (vectorStore == null) {
      log.warn("Unknown Redis vector store '{}'; configured stores: {}", storeName, redisVectorStores.keySet());
      return;
    }
    Path root = Path.of(sourceFolder);
    if (!Files.isDirectory(root)) {
      log.warn("app.rag.ingest.source-folder '{}' is not a directory; skipping ingest", sourceFolder);
      return;
    }

    log.info("Starting JSON ingest from '{}' into Redis store '{}'", root, storeName);
    Instant start = Instant.now();
    int[] counters = ingestFolder(root, vectorStore); // {processed, failed}
    Duration elapsed = Duration.between(start, Instant.now());

    System.out.printf(
        "JSON ingest finished: %d file(s) processed, %d failed, elapsed %d.%03ds%n",
        counters[0], counters[1], elapsed.toSeconds(), elapsed.toMillisPart());
    log.info("JSON ingest finished: {} file(s) processed, {} failed, elapsed {}",
        counters[0], counters[1], elapsed);
  }

  private int[] ingestFolder(Path root, VectorStore vectorStore) {
    int processed = 0;
    int failed = 0;
    List<Document> batch = new ArrayList<>(BATCH_SIZE);

    try (Stream<Path> paths = Files.walk(root)) {
      List<Path> jsonFiles = paths
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
          .toList();

      for (Path file : jsonFiles) {
        try {
          batch.add(toDocument(file));
          processed++;
          if (batch.size() >= BATCH_SIZE) {
            vectorStore.add(batch);
            batch = new ArrayList<>(BATCH_SIZE);
          }
        } catch (Exception e) {
          failed++;
          log.warn("Failed to ingest '{}': {}", file, e.getMessage());
        }
      }
      if (!batch.isEmpty()) {
        vectorStore.add(batch);
      }
    } catch (IOException e) {
      log.error("Failed to walk folder '{}'", root, e);
    }
    return new int[] {processed, failed};
  }

  private Document toDocument(Path file) throws IOException {
    JsonDocumentPayload payload = objectMapper.readValue(file.toFile(), JsonDocumentPayload.class);
    if (!StringUtils.hasText(payload.content())) {
      throw new IOException("missing or blank 'content' field");
    }
    Map<String, Object> metadata = new LinkedHashMap<>(payload.metadataOrEmpty());
    metadata.putIfAbsent("sourceFile", file.toAbsolutePath().toString());
    return new Document(payload.content(), metadata);
  }
}
