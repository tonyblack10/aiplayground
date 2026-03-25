package io.tonyblack10.aiplayground.rag.model;

import java.time.LocalDateTime;

public record DocumentEntry(
    String id,
    String source,
    String contentPreview,
    String storeId,
    LocalDateTime ingestedAt
) {}
