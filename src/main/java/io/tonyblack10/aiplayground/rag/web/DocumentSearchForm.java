package io.tonyblack10.aiplayground.rag.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DocumentSearchForm(

    @NotBlank(message = "A consulta de busca é obrigatória")
    String query,

    @Min(value = 1, message = "O número de resultados deve ser pelo menos 1")
    @Max(value = 100, message = "O número de resultados não pode exceder 100")
    Integer topK,

    @DecimalMin(value = "0.0", message = "O limiar de similaridade deve ser entre 0.0 e 1.0")
    @DecimalMax(value = "1.0", message = "O limiar de similaridade deve ser entre 0.0 e 1.0")
    Double similarityThreshold,

    String filterExpression

) {}
