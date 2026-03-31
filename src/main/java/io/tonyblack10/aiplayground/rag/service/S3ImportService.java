package io.tonyblack10.aiplayground.rag.service;

import io.tonyblack10.aiplayground.rag.model.S3ImportResult;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;

@Service
public class S3ImportService {

  private static final Logger log = LoggerFactory.getLogger(S3ImportService.class);
  private static final long REQUEST_DELAY_MS = 500L;
  private static final int MAX_RETRIES = 3;

  private final S3Client s3Client;
  private final DocumentParserService parserService;
  private final String accessKeyId;
  private final String secretAccessKey;

  public S3ImportService(
      @Value("${app.s3.region:us-east-1}") String region,
      @Value("${app.s3.access-key-id:}") String accessKeyId,
      @Value("${app.s3.secret-access-key:}") String secretAccessKey,
      @Value("${app.s3.endpoint:}") String endpoint,
      DocumentParserService parserService) {
    this.accessKeyId = accessKeyId;
    this.secretAccessKey = secretAccessKey;
    this.parserService = parserService;

    S3ClientBuilder builder = S3Client.builder().region(Region.of(region));

    if (!accessKeyId.isBlank() && !secretAccessKey.isBlank()) {
      builder.credentialsProvider(
          StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }

    if (!endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint))
             .forcePathStyle(true);
    }

    this.s3Client = builder.build();
  }

  public Mono<S3ImportResult> importFromBucket(String bucketName, String prefix, List<String> extensions) {
    return Mono.fromCallable(() -> listAndImport(bucketName, prefix, extensions))
        .subscribeOn(Schedulers.boundedElastic());
  }

  private S3ImportResult listAndImport(String bucketName, String prefix, List<String> extensions) {
    List<S3Object> objects = listObjects(bucketName, prefix, extensions);
    log.info("Found {} matching file(s) in bucket '{}' (prefix='{}', formats={})",
        objects.size(), bucketName, prefix, extensions);

    int filesIngested = 0;
    int chunksIngested = 0;
    List<String> errors = new ArrayList<>();
    List<Document> allDocuments = new ArrayList<>();

    for (int i = 0; i < objects.size(); i++) {
      if (i > 0) {
        sleepMs(REQUEST_DELAY_MS);
      }
      S3Object obj = objects.get(i);
      String key = obj.key();
      try {
        List<Document> docs = downloadAndParse(bucketName, key);
        if (!docs.isEmpty()) {
          allDocuments.addAll(docs);
          chunksIngested += docs.size();
          filesIngested++;
          log.info("Ingested '{}' ({} chunk(s))", key, docs.size());
        } else {
          log.warn("No content extracted from '{}'", key);
        }
      } catch (Exception e) {
        log.warn("Failed to process '{}': {}", key, e.getMessage());
        errors.add(key + ": " + e.getMessage());
      }
    }

    log.info("S3 import finished: {}/{} file(s) ingested, {} chunk(s), {} error(s)",
        filesIngested, objects.size(), chunksIngested, errors.size());

    return new S3ImportResult(objects.size(), filesIngested, chunksIngested, errors, allDocuments);
  }

  private List<S3Object> listObjects(String bucketName, String prefix, List<String> extensions) {
    List<S3Object> result = new ArrayList<>();
    String continuationToken = null;

    do {
      ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
          .bucket(bucketName);

      if (prefix != null && !prefix.isBlank()) {
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        reqBuilder.prefix(normalizedPrefix);
      }

      if (continuationToken != null) {
        reqBuilder.continuationToken(continuationToken);
      }

      ListObjectsV2Response response = s3Client.listObjectsV2(reqBuilder.build());

      response.contents().stream()
          .filter(obj -> !obj.key().endsWith("/"))
          .filter(obj -> matchesExtension(obj.key(), extensions))
          .forEach(result::add);

      continuationToken = Boolean.TRUE.equals(response.isTruncated())
          ? response.nextContinuationToken() : null;

      if (continuationToken != null) {
        sleepMs(REQUEST_DELAY_MS);
      }
    } while (continuationToken != null);

    return result;
  }

  private List<Document> downloadAndParse(String bucketName, String key) {
    int attempt = 0;
    while (true) {
      attempt++;
      try {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();
        byte[] bytes = s3Client.getObjectAsBytes(request).asByteArray();
        String filename = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        List<Document> docs = parserService.parseTika(bytes, filename).block();
        return docs != null ? docs : List.of();
      } catch (SdkServiceException e) {
        int status = e.statusCode();
        if ((status == 429 || status == 503) && attempt <= MAX_RETRIES) {
          log.warn("Rate limited on '{}', retry {}/{}", key, attempt, MAX_RETRIES);
          sleepMs(REQUEST_DELAY_MS * attempt);
        } else {
          throw e;
        }
      }
    }
  }

  private boolean matchesExtension(String key, List<String> extensions) {
    String lowerKey = key.toLowerCase();
    return extensions.stream()
        .anyMatch(ext -> lowerKey.endsWith("." + ext.toLowerCase().replaceAll("^\\.", "")));
  }

  private void sleepMs(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
