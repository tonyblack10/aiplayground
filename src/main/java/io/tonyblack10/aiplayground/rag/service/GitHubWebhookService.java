package io.tonyblack10.aiplayground.rag.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tonyblack10.aiplayground.config.github.GitHubWebhookProperties;
import io.tonyblack10.aiplayground.config.github.GitHubWebhookProperties.WebhookConfig;
import io.tonyblack10.aiplayground.rag.model.DocumentImportRecord;
import io.tonyblack10.aiplayground.rag.registry.DocumentRegistry;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class GitHubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);

    private final GitHubWebhookProperties webhookProperties;
    private final DocumentParserService parserService;
    private final VectorStoreRegistry vectorStoreRegistry;
    private final DocumentRegistry documentRegistry;
    private final ImportRecordS3Repository importRecordRepository;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public GitHubWebhookService(
        GitHubWebhookProperties webhookProperties,
        DocumentParserService parserService,
        VectorStoreRegistry vectorStoreRegistry,
        DocumentRegistry documentRegistry,
        ImportRecordS3Repository importRecordRepository,
        ObjectMapper objectMapper,
        WebClient.Builder webClientBuilder,
        @Value("${app.github.token:}") String githubToken
    ) {
        this.webhookProperties = webhookProperties;
        this.parserService = parserService;
        this.vectorStoreRegistry = vectorStoreRegistry;
        this.documentRegistry = documentRegistry;
        this.importRecordRepository = importRecordRepository;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder
            .defaultHeader(HttpHeaders.AUTHORIZATION, "token " + githubToken)
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
    }

    public Mono<Void> handlePush(byte[] rawPayload, String signature) {
        return Mono.fromCallable(() -> objectMapper.readValue(rawPayload, PushPayload.class))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(push -> {
                String repoFullName = push.repository().fullName();
                Optional<WebhookConfig> configOpt = findConfig(repoFullName);
                if (configOpt.isEmpty()) {
                    log.debug("No webhook config for repo '{}', ignoring push", repoFullName);
                    return Mono.empty();
                }
                WebhookConfig config = configOpt.get();
                try {
                    validateSignature(rawPayload, signature, config.getSecret());
                } catch (IllegalArgumentException e) {
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage()));
                }
                String expectedRef = "refs/heads/" + config.getBranch();
                if (!expectedRef.equals(push.ref())) {
                    log.debug("Push ref '{}' does not match configured branch '{}', ignoring",
                        push.ref(), expectedRef);
                    return Mono.empty();
                }

                List<String> eligiblePaths = push.commits().stream()
                    .flatMap(c -> c.added() != null ? c.added().stream() : Stream.empty())
                    .distinct()
                    .filter(path -> isEligibleFile(path, config.getDirectory()))
                    .toList();

                if (eligiblePaths.isEmpty()) {
                    log.info("Push to {} on '{}' contained no new eligible .md files, ignoring",
                        repoFullName, config.getBranch());
                    return Mono.empty();
                }

                String[] parts = repoFullName.split("/", 2);
                String owner = parts[0], repo = parts[1];
                String ref = push.afterSha();

                return Flux.fromIterable(eligiblePaths)
                    .flatMap(path -> fetchRawFile(owner, repo, ref, path)
                        .flatMap(bytes -> ingestFile(path, bytes, config.getStoreId()))
                        .onErrorResume(e -> {
                            log.warn("Skipping {}: {}", path, e.getMessage());
                            return Mono.just(0);
                        }), 4)
                    .reduce(0, Integer::sum)
                    .doOnNext(totalChunks -> {
                        log.info("Webhook push ingestion complete for {}: {} new file(s), {} chunk(s)",
                            repoFullName, eligiblePaths.size(), totalChunks);
                        saveAuditRecord(repoFullName, push.ref(), config.getStoreId(),
                            eligiblePaths.size(), totalChunks, "webhook-push");
                    })
                    .then();
            });
    }

    public Mono<Void> handlePullRequest(byte[] rawPayload, String signature) {
        return Mono.fromCallable(() -> objectMapper.readValue(rawPayload, PullRequestPayload.class))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(event -> {
                String repoFullName = event.repository().fullName();
                Optional<WebhookConfig> configOpt = findConfig(repoFullName);
                if (configOpt.isEmpty()) {
                    log.debug("No webhook config for repo '{}', ignoring PR event", repoFullName);
                    return Mono.empty();
                }
                WebhookConfig config = configOpt.get();
                try {
                    validateSignature(rawPayload, signature, config.getSecret());
                } catch (IllegalArgumentException e) {
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage()));
                }

                PullRequest pr = event.pullRequest();
                if (!"closed".equals(event.action())
                        || !pr.merged()
                        || !config.getBranch().equals(pr.base().ref())) {
                    log.debug("PR event ignored (action={}, merged={}, base={})",
                        event.action(), pr.merged(), pr.base() != null ? pr.base().ref() : "null");
                    return Mono.empty();
                }

                String[] parts = repoFullName.split("/", 2);
                String owner = parts[0], repo = parts[1];
                String ref = pr.mergeCommitSha();

                return fetchPrFiles(owner, repo, pr.number())
                    .filter(f -> "added".equals(f.status()))
                    .map(PrFile::filename)
                    .filter(path -> isEligibleFile(path, config.getDirectory()))
                    .collectList()
                    .flatMap(eligiblePaths -> {
                        if (eligiblePaths.isEmpty()) {
                            log.info("Merged PR #{} in {} had no new eligible .md files",
                                pr.number(), repoFullName);
                            return Mono.empty();
                        }
                        return Flux.fromIterable(eligiblePaths)
                            .flatMap(path -> fetchRawFile(owner, repo, ref, path)
                                .flatMap(bytes -> ingestFile(path, bytes, config.getStoreId()))
                                .onErrorResume(e -> {
                                    log.warn("Skipping {}: {}", path, e.getMessage());
                                    return Mono.just(0);
                                }), 4)
                            .reduce(0, Integer::sum)
                            .doOnNext(totalChunks -> {
                                log.info("Webhook PR merge ingestion complete for PR #{} in {}: {} file(s), {} chunk(s)",
                                    pr.number(), repoFullName, eligiblePaths.size(), totalChunks);
                                saveAuditRecord(repoFullName, "PR#" + pr.number(),
                                    config.getStoreId(), eligiblePaths.size(), totalChunks, "webhook-pr-merge");
                            })
                            .then();
                    });
            });
    }

    private void validateSignature(byte[] payload, String signature, String secret) {
        if (signature == null || !signature.startsWith("sha256=")) {
            throw new IllegalArgumentException("Missing or malformed X-Hub-Signature-256 header");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload);
            String expected = "sha256=" + HexFormat.of().formatHex(computed);
            if (!MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("Signature mismatch");
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private Optional<WebhookConfig> findConfig(String repoFullName) {
        return webhookProperties.getWebhooks().stream()
            .filter(c -> c.fullName().equals(repoFullName))
            .findFirst();
    }

    private boolean isEligibleFile(String path, String directory) {
        if (!path.toLowerCase().endsWith(".md")) return false;
        if (directory == null || directory.isBlank()) return true;
        String dir = directory.endsWith("/") ? directory : directory + "/";
        return path.startsWith(dir);
    }

    private Mono<byte[]> fetchRawFile(String owner, String repo, String ref, String path) {
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + ref;
        return webClient.get()
            .uri(url)
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, resp ->
                resp.bodyToMono(String.class).map(body ->
                    new IllegalStateException("GitHub API error fetching " + path
                        + " (" + resp.statusCode() + "): " + body)))
            .bodyToMono(ContentsResponse.class)
            .map(r -> Base64.getMimeDecoder().decode(r.content()))
            .doOnError(e -> log.warn("Failed to fetch {}: {}", url, e.getMessage()));
    }

    private Flux<PrFile> fetchPrFiles(String owner, String repo, long prNumber) {
        // TODO: paginate via Link response header for PRs with more than 100 changed files
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/files?per_page=100";
        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToFlux(PrFile.class)
            .doOnError(e -> log.warn("Failed to fetch PR files for {}/{} #{}: {}",
                owner, repo, prNumber, e.getMessage()));
    }

    private Mono<Integer> ingestFile(String path, byte[] bytes, String storeId) {
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return parserService.parse(bytes, filename)
            .flatMap(docs -> {
                docs.forEach(doc -> doc.getMetadata().put("source", path));
                return Mono.fromCallable(() -> {
                    VectorStore store = vectorStoreRegistry.getStore(storeId);
                    store.add(docs);
                    documentRegistry.register(storeId, docs);
                    return docs.size();
                }).subscribeOn(Schedulers.boundedElastic());
            });
    }

    private void saveAuditRecord(String repoFullName, String ref, String storeId,
                                  int filesIngested, int chunksIngested, String trigger) {
        try {
            importRecordRepository.save(new DocumentImportRecord(
                UUID.randomUUID().toString(),
                repoFullName,
                Instant.now(),
                "github-webhook",
                "webhook",
                storeId,
                Map.of(
                    "repoFullName", repoFullName,
                    "ref", ref,
                    "filesIngested", String.valueOf(filesIngested),
                    "chunksIngested", String.valueOf(chunksIngested),
                    "trigger", trigger
                )
            ));
        } catch (Exception e) {
            log.warn("Failed to save webhook audit record for {}: {}", repoFullName, e.getMessage());
        }
    }

    // ── Payload models ──────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PushPayload(
        String ref,
        @JsonProperty("after") String afterSha,
        List<PushCommit> commits,
        Repository repository
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PushCommit(
        List<String> added
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PullRequestPayload(
        String action,
        @JsonProperty("pull_request") PullRequest pullRequest,
        Repository repository
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PullRequest(
        boolean merged,
        @JsonProperty("merge_commit_sha") String mergeCommitSha,
        PrBase base,
        long number
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PrBase(String ref) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Repository(
        @JsonProperty("full_name") String fullName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PrFile(
        String filename,
        String status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentsResponse(
        String content,
        String encoding
    ) {}
}
