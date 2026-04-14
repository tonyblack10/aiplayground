package io.tonyblack10.aiplayground.rag.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record S3ImportForm(

    @NotBlank(message = "O nome do bucket é obrigatório")
    String bucketName,

    String prefix,

    @NotEmpty(message = "Selecione ao menos um formato de arquivo")
    List<String> fileFormats

) {}
