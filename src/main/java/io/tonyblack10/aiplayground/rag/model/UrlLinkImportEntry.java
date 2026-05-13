package io.tonyblack10.aiplayground.rag.model;

public record UrlLinkImportEntry(
    String url,
    boolean success,
    int chunksIngested,
    String errorMessage
) {}
