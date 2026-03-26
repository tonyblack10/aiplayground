package io.tonyblack10.aiplayground.rag.service;

import java.util.List;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Mono;

public interface GitHubImporter {

  Mono<List<Document>> importFromGitHub(String repoUrl, String branch);
}
