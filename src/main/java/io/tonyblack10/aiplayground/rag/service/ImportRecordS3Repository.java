package io.tonyblack10.aiplayground.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.tonyblack10.aiplayground.rag.model.DocumentImportRecord;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@Repository
public class ImportRecordS3Repository {

  private static final Logger log = LoggerFactory.getLogger(ImportRecordS3Repository.class);

  private final S3Client s3Client;
  private final ObjectMapper objectMapper;
  private final String recordsBucket;
  private final String recordsPrefix;

  public ImportRecordS3Repository(
      @Value("${app.s3.region:us-east-1}") String region,
      @Value("${app.s3.access-key-id:}") String accessKeyId,
      @Value("${app.s3.secret-access-key:}") String secretAccessKey,
      @Value("${app.s3.endpoint:}") String endpoint,
      @Value("${app.s3.records-bucket:}") String recordsBucket,
      @Value("${app.s3.records-prefix:rag-records}") String recordsPrefix) {

    this.recordsBucket = recordsBucket;
    this.recordsPrefix = recordsPrefix.endsWith("/") ? recordsPrefix : recordsPrefix + "/";
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    S3ClientBuilder builder = S3Client.builder().region(Region.of(region));

    if (!accessKeyId.isBlank() && !secretAccessKey.isBlank()) {
      builder.credentialsProvider(
          StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }

    if (!endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
    }

    this.s3Client = builder.build();
  }

  public void save(DocumentImportRecord record) {
    if (recordsBucket.isBlank()) {
      log.debug("No records bucket configured, skipping record save for {}", record.id());
      return;
    }
    try {
      byte[] json = objectMapper.writeValueAsBytes(record);
      String key = recordsPrefix + record.id() + ".json";
      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(recordsBucket)
              .key(key)
              .contentType("application/json")
              .build(),
          RequestBody.fromBytes(json));
      log.info("Saved import record {} ({}) to s3://{}/{}", record.id(), record.source(), recordsBucket, key);
    } catch (Exception e) {
      log.warn("Failed to save import record {} to S3: {}", record.id(), e.getMessage());
    }
  }

  public List<DocumentImportRecord> findAll() {
    if (recordsBucket.isBlank()) {
      log.debug("No records bucket configured, returning empty record list");
      return List.of();
    }
    List<DocumentImportRecord> records = new ArrayList<>();
    String continuationToken = null;

    do {
      ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
          .bucket(recordsBucket)
          .prefix(recordsPrefix);

      if (continuationToken != null) {
        reqBuilder.continuationToken(continuationToken);
      }

      ListObjectsV2Response response = s3Client.listObjectsV2(reqBuilder.build());

      for (S3Object obj : response.contents()) {
        if (!obj.key().endsWith(".json")) continue;
        try {
          byte[] bytes = s3Client.getObjectAsBytes(
              GetObjectRequest.builder().bucket(recordsBucket).key(obj.key()).build()
          ).asByteArray();
          records.add(objectMapper.readValue(bytes, DocumentImportRecord.class));
        } catch (Exception e) {
          log.warn("Failed to read import record from {}: {}", obj.key(), e.getMessage());
        }
      }

      continuationToken = Boolean.TRUE.equals(response.isTruncated())
          ? response.nextContinuationToken() : null;

    } while (continuationToken != null);

    log.info("Loaded {} import records from s3://{}/{}", records.size(), recordsBucket, recordsPrefix);
    return records;
  }
}
