package io.tonyblack10.aiplayground.rag.model;

import java.time.LocalDateTime;
import java.util.Map;

public record DocumentEntry(
    String id,
    String source,
    String contentPreview,
    String storeId,
    LocalDateTime ingestedAt,
    Map<String, Object> metadata
) {}
