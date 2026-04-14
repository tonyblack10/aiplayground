package io.tonyblack10.aiplayground.rag.web;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record DeleteDocumentsForm(

    @NotEmpty(message = "Nenhum documento selecionado para exclusão")
    List<String> ids

) {}
