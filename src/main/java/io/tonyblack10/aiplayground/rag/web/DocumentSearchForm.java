package io.tonyblack10.aiplayground.rag.web;

public record DocumentSearchForm(
    String query,
    Integer topK,
    Double similarityThreshold,
    String filterExpression
) {}
