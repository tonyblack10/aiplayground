package io.tonyblack10.aiplayground.rag.web;

import jakarta.validation.constraints.NotBlank;

public record UrlLinksImportForm(
    @NotBlank(message = "Informe ao menos um link")
    String links
) {}
