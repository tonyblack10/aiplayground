package io.tonyblack10.aiplayground.rag.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfluenceImportForm(

    @NotBlank(message = "A chave do Space é obrigatória")
    @Pattern(
        regexp = "[A-Za-z][A-Za-z0-9_]{0,49}",
        message = "A chave do Space deve começar com uma letra e conter apenas letras, números e underscores (máx. 50 caracteres)"
    )
    String spaceKey,

    String pageIds

) {}
