package io.tonyblack10.aiplayground.rag.web;

public record GitHubImportForm(
    String repoUrl,
    String branch,
    String folders
) {}
