package io.tonyblack10.aiplayground.rag.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Primary
@Service
public class JGitGitHubImportService implements GitHubImporter {

  private static final Logger log = LoggerFactory.getLogger(JGitGitHubImportService.class);

  private final String githubToken;
  private final Path cloneBaseDir;
  private final DocumentParserService parserService;

  public JGitGitHubImportService(
      @Value("${app.github.token}") String githubToken,
      @Value("${app.github.clone-dir}") String cloneDir,
      DocumentParserService parserService) {
    this.githubToken = githubToken;
    this.cloneBaseDir = Path.of(cloneDir);
    this.parserService = parserService;
  }

  @Override
  public Mono<List<Document>> importFromGitHub(String repoUrl, String branch, List<String> folders) {
    return Mono.fromCallable(() -> cloneAndParse(repoUrl, branch, folders))
        .subscribeOn(Schedulers.boundedElastic())
        .map(lists -> lists.stream().flatMap(Collection::stream).toList());
  }

  private List<List<Document>> cloneAndParse(String repoUrl, String branch, List<String> folders) throws Exception {
    String repoName = extractRepoName(repoUrl);
    Path cloneDir = cloneBaseDir.resolve(repoName + "-" + UUID.randomUUID());

    try {
      cloneRepository(repoUrl, branch, cloneDir);
      return collectMarkdownFiles(cloneDir, folders);
    } finally {
      deleteDirectory(cloneDir);
    }
  }

  private void cloneRepository(String repoUrl, String branch, Path targetDir) throws Exception {
    Files.createDirectories(targetDir.getParent());

    try (Git git = Git.cloneRepository()
        .setURI(repoUrl)
        .setDirectory(targetDir.toFile())
        .setBranch("refs/heads/" + branch)
        .setDepth(1)
        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubToken, ""))
        .call()) {
      log.info("Cloned {} (branch: {}) to {} via JGit", repoUrl, branch, targetDir);
    }
  }

  private List<List<Document>> collectMarkdownFiles(Path cloneDir, List<String> folders) throws IOException {
    List<List<Document>> result = new ArrayList<>();
    List<Path> searchRoots = (folders == null || folders.isEmpty())
        ? List.of(cloneDir)
        : folders.stream()
              .map(f -> cloneDir.resolve(f.replaceAll("^/+", "")))
              .filter(Files::isDirectory)
              .toList();

    try (Stream<Path> walk = searchRoots.stream()
        .flatMap(root -> {
          try {
            return Files.walk(root);
          } catch (IOException e) {
            log.warn("Could not walk folder {}: {}", root, e.getMessage());
            return Stream.empty();
          }
        })) {
      List<Path> mdFiles = walk
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md"))
          .distinct()
          .toList();

      log.info("Found {} .md files to ingest", mdFiles.size());

      for (Path file : mdFiles) {
        try {
          byte[] bytes = Files.readAllBytes(file);
          String filename = file.getFileName().toString();
          List<Document> docs = parserService.parse(bytes, filename).block();
          if (docs != null && !docs.isEmpty()) {
            result.add(docs);
          }
        } catch (Exception e) {
          log.warn("Skipping {}: {}", file, e.getMessage());
        }
      }
    }
    return result;
  }

  private void deleteDirectory(Path dir) {
    try {
      if (Files.exists(dir)) {
        try (Stream<Path> walk = Files.walk(dir)) {
          walk.sorted(Comparator.reverseOrder())
              .forEach(p -> {
                try {
                  Files.delete(p);
                } catch (IOException e) {
                  log.warn("Could not delete {}: {}", p, e.getMessage());
                }
              });
        }
        log.info("Deleted clone directory: {}", dir);
      }
    } catch (IOException e) {
      log.warn("Failed to clean up clone directory {}: {}", dir, e.getMessage());
    }
  }

  private String extractRepoName(String repoUrl) {
    String normalized = repoUrl.trim().replaceAll("/$", "").replace(".git", "");
    int lastSlash = normalized.lastIndexOf('/');
    int lastColon = normalized.lastIndexOf(':');
    int start = Math.max(lastSlash, lastColon);
    return start >= 0 ? normalized.substring(start + 1) : normalized;
  }
}
