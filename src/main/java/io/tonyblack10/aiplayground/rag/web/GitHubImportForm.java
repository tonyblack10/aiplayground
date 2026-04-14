package io.tonyblack10.aiplayground.rag.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GitHubImportForm(

    @NotBlank(message = "A URL do repositório é obrigatória")
    @Pattern(regexp = "https://.*", message = "A URL do repositório deve começar com https://")
    String repoUrl,

    @NotBlank(message = "O branch é obrigatório")
    String branch,

    String folders

) {}
