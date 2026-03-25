package io.tonyblack10.aiplayground.rag.model;

public record VectorStoreInfo(
    String id,
    String displayName,
    String type,
    boolean primary,
    int documentCount
) {}
