package io.tonyblack10.aiplayground.config.rag.vectorstore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import io.tonyblack10.aiplayground.rag.registry.DocumentRegistry;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;

@Configuration
public class SimpleVectorStoreConfig {

  @Autowired
  private DocumentRegistry documentRegistry;

  private static final Logger log = LoggerFactory.getLogger(SimpleVectorStoreConfig.class);

  @Value("vectorstore.json")
  private String vectorStoreName;

  @Value("classpath:/docs/faq.txt")
  private Resource faq;

  @Bean
  @Primary
  public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) throws IOException {
    var simpleVectorStore = SimpleVectorStore
        .builder(embeddingModel).build();

    var vectorStoreFile = getVectorStoreFile();
    if (vectorStoreFile.exists()) {
      log.info("Vector Store File Exists,");
      simpleVectorStore.load(vectorStoreFile);
    } else {
      log.info("Vector Store File Does Not Exist, loading documents");
      TextReader textReader = new TextReader(faq);
      textReader.getCustomMetadata().put("filename", "faq.txt");
      List<Document> documents = textReader.get();
      TextSplitter textSplitter = new TokenTextSplitter();
      List<Document> splitDocuments = textSplitter.apply(documents);
      simpleVectorStore.add(splitDocuments);
      documentRegistry.register("simpleVectorStore", splitDocuments);
      simpleVectorStore.save(vectorStoreFile);
    }
    return simpleVectorStore;
  }

  private File getVectorStoreFile() {
    var path = Paths.get("src", "main", "resources", "data");
    String absolutePath = path.toFile().getAbsolutePath() + "/" + vectorStoreName;
    return new File(absolutePath);
  }


}
