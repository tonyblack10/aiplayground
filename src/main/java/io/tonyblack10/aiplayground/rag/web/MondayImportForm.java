package io.tonyblack10.aiplayground.rag.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MondayImportForm(

    @NotBlank(message = "O ID do Board é obrigatório")
    @Pattern(regexp = "\\d+", message = "O ID do Board deve conter apenas números")
    String boardId

) {}
