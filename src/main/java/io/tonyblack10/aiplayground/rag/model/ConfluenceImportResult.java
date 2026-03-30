package io.tonyblack10.aiplayground.rag.model;

import java.util.List;
import org.springframework.ai.document.Document;

public record ConfluenceImportResult(
    int pagesProcessed,
    int pagesIngested,
    int chunksIngested,
    List<String> errors,
    List<Document> documents
) {}
