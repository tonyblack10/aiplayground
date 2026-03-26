package io.tonyblack10.aiplayground.rag.model;

public record FileUploadResult(
    String filename,
    boolean success,
    int chunksIngested,
    String errorMessage
) {}
