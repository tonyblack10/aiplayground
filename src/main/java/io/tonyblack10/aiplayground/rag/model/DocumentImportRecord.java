package io.tonyblack10.aiplayground.rag.model;

import java.time.Instant;
import java.util.Map;

public record DocumentImportRecord(
    String id,
    String documentName,
    Instant importDate,
    String source,
    String importedBy,
    String storeId,
    Map<String, String> additionalData
) {}
