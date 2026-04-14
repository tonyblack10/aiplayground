package io.tonyblack10.aiplayground.rag.web;

import java.util.List;

public record DeleteDocumentsForm(
    List<String> ids
) {}
