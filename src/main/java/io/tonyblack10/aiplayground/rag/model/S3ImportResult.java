package io.tonyblack10.aiplayground.rag.model;

import java.util.List;
import org.springframework.ai.document.Document;

public record S3ImportResult(
    int filesProcessed,
    int filesIngested,
    int chunksIngested,
    List<String> errors,
    List<Document> documents
) {}
