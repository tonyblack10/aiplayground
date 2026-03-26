package io.tonyblack10.aiplayground.rag.service;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class DocumentParserService {

  private static final TokenTextSplitter SPLITTER = new TokenTextSplitter();

  public Mono<List<Document>> parse(FilePart filePart) {
    return DataBufferUtils.join(filePart.content())
        .map(dataBuffer -> {
          byte[] bytes = new byte[dataBuffer.readableByteCount()];
          dataBuffer.read(bytes);
          DataBufferUtils.release(dataBuffer);
          return bytes;
        })
        .flatMap(bytes -> parse(bytes, filePart.filename()));
  }

  public Mono<List<Document>> parse(byte[] bytes, String filename) {
    return Mono.fromCallable(() -> parseBytes(bytes, filename))
        .subscribeOn(Schedulers.boundedElastic());
  }

  public Mono<List<Document>> parseTika(byte[] bytes, String filename) {
    return Mono.fromCallable(() -> parseTikaBytes(bytes, filename))
        .subscribeOn(Schedulers.boundedElastic());
  }

  private List<Document> parseBytes(byte[] bytes, String filename) {
    String lower = filename.toLowerCase();
    ByteArrayResource resource = namedResource(bytes, filename);
    List<Document> docs;
    if (lower.endsWith(".pdf")) {
      docs = parsePdf(resource);
    } else if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
      docs = parseMd(resource);
    } else {
      docs = parseTxt(resource, filename);
    }
    docs.forEach(doc -> doc.getMetadata().put("source", filename));
    return SPLITTER.apply(docs);
  }

  private List<Document> parsePdf(ByteArrayResource resource) {
    return new PagePdfDocumentReader(resource).get();
  }

  private List<Document> parseMd(ByteArrayResource resource) {
    return new MarkdownDocumentReader(resource, MarkdownDocumentReaderConfig.defaultConfig()).get();
  }

  private List<Document> parseTxt(ByteArrayResource resource, String filename) {
    TextReader reader = new TextReader(resource);
    reader.getCustomMetadata().put("filename", filename);
    return reader.get();
  }

  private List<Document> parseTikaBytes(byte[] bytes, String filename) {
    ByteArrayResource resource = namedResource(bytes, filename);
    List<Document> docs = new TikaDocumentReader(resource).get();
    docs.forEach(doc -> doc.getMetadata().put("source", filename));
    return SPLITTER.apply(docs);
  }

  private ByteArrayResource namedResource(byte[] bytes, String filename) {
    return new ByteArrayResource(bytes) {
      @Override
      public String getFilename() {
        return filename;
      }
    };
  }
}
