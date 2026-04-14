package io.tonyblack10.aiplayground.rag.web;

import java.util.List;

public record S3ImportForm(
    String bucketName,
    String prefix,
    List<String> fileFormats
) {}
