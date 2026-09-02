package io.tonyblack10.aiplayground.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for {@code AgenticRagSearchService}'s query rewriting + multi-query fan-out/fan-in
 * retrieval.
 *
 * <p>Example configuration:
 * <pre>
 * app:
 *   rag:
 *     agentic:
 *       enabled: true
 *       number-of-queries: 3
 *       complexity-word-threshold: 6
 * </pre>
 */
@ConfigurationProperties(prefix = "app.rag.agentic")
public class AgenticRagProperties {

  /**
   * Master switch; when false, {@code searchRagDocuments} always does a single-query search
   * with no rewriting or expansion.
   */
  private boolean enabled = true;

  /** How many sub-queries {@code MultiQueryExpander} generates (including the original) for complex queries. */
  private int numberOfQueries = 3;

  /** Queries with more words than this (or containing 'and'/'or'/a comma) are treated as complex and expanded. */
  private int complexityWordThreshold = 6;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getNumberOfQueries() {
    return numberOfQueries;
  }

  public void setNumberOfQueries(int numberOfQueries) {
    this.numberOfQueries = numberOfQueries;
  }

  public int getComplexityWordThreshold() {
    return complexityWordThreshold;
  }

  public void setComplexityWordThreshold(int complexityWordThreshold) {
    this.complexityWordThreshold = complexityWordThreshold;
  }
}
