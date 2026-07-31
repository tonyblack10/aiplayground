package io.tonyblack10.aiplayground.rag.ingest;

import java.util.Map;

/**
 * Maps the on-disk JSON structure: a free-form {@code metadata} object and a markdown
 * {@code content} string. Jackson's untyped deserialization already resolves each metadata
 * value to its natural Java type (String, Boolean, Integer/Long/Double, List, Map), so the
 * map can be handed to the vector store as-is without any further type coercion.
 */
public record JsonDocumentPayload(Map<String, Object> metadata, String content) {

  public Map<String, Object> metadataOrEmpty() {
    return metadata != null ? metadata : Map.of();
  }
}
