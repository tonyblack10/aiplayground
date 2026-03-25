package io.tonyblack10.aiplayground.rag.registry;

import io.tonyblack10.aiplayground.rag.model.DocumentEntry;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Component
public class DocumentRegistry {

  private final ConcurrentHashMap<String, ConcurrentHashMap<String, DocumentEntry>> registry =
      new ConcurrentHashMap<>();

  public void register(String storeId, List<Document> documents) {
    ConcurrentHashMap<String, DocumentEntry> storeEntries =
        registry.computeIfAbsent(storeId, k -> new ConcurrentHashMap<>());

    for (Document doc : documents) {
      String source = extractSource(doc);
      String preview = doc.getText() != null
          ? doc.getText().substring(0, Math.min(200, doc.getText().length()))
          : "";
      DocumentEntry entry = new DocumentEntry(doc.getId(), source, preview, storeId, LocalDateTime.now());
      storeEntries.put(doc.getId(), entry);
    }
  }

  public List<DocumentEntry> getDocuments(String storeId) {
    ConcurrentHashMap<String, DocumentEntry> storeEntries = registry.get(storeId);
    if (storeEntries == null) return List.of();
    return storeEntries.values().stream()
        .sorted(Comparator.comparing(DocumentEntry::ingestedAt).reversed())
        .toList();
  }

  public void remove(String storeId, List<String> ids) {
    ConcurrentHashMap<String, DocumentEntry> storeEntries = registry.get(storeId);
    if (storeEntries != null) {
      ids.forEach(storeEntries::remove);
    }
  }

  public int count(String storeId) {
    ConcurrentHashMap<String, DocumentEntry> storeEntries = registry.get(storeId);
    return storeEntries == null ? 0 : storeEntries.size();
  }

  private String extractSource(Document doc) {
    Object source = doc.getMetadata().get("source");
    if (source != null) return source.toString();
    Object filename = doc.getMetadata().get("filename");
    if (filename != null) return filename.toString();
    return "unknown";
  }
}
