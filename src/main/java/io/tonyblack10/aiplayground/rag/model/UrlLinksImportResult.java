package io.tonyblack10.aiplayground.rag.model;

import java.util.List;
import org.springframework.ai.document.Document;

public record UrlLinksImportResult(
    int totalProcessed,
    int succeeded,
    int failed,
    List<UrlLinkImportEntry> entries,
    List<Document> documents
) {}
