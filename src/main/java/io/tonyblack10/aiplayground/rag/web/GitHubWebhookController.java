package io.tonyblack10.aiplayground.rag.web;

import io.tonyblack10.aiplayground.rag.service.GitHubWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/webhooks")
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final GitHubWebhookService webhookService;

    public GitHubWebhookController(GitHubWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/github")
    public Mono<ResponseEntity<String>> handleGitHubWebhook(
        @RequestHeader(value = "X-GitHub-Event", defaultValue = "unknown") String event,
        @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
        @RequestBody byte[] payload
    ) {
        log.debug("Received GitHub webhook event: {}", event);

        return switch (event) {
            case "push" ->
                webhookService.handlePush(payload, signature)
                    .thenReturn(ResponseEntity.ok("Push processed"))
                    .onErrorResume(e -> handleError(event, e));

            case "pull_request" ->
                webhookService.handlePullRequest(payload, signature)
                    .thenReturn(ResponseEntity.ok("Pull request processed"))
                    .onErrorResume(e -> handleError(event, e));

            case "ping" ->
                Mono.just(ResponseEntity.ok("pong"));

            default -> {
                log.debug("Ignoring unsupported GitHub event type: {}", event);
                yield Mono.just(ResponseEntity.ok("Event ignored"));
            }
        };
    }

    private Mono<ResponseEntity<String>> handleError(String event, Throwable e) {
        if (e instanceof ResponseStatusException rse) {
            log.warn("Webhook {} rejected: {}", event, e.getMessage());
            return Mono.just(ResponseEntity.status(rse.getStatusCode()).body(e.getMessage()));
        }
        log.error("Webhook {} processing failed", event, e);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Internal error processing webhook"));
    }
}
