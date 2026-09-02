package io.tonyblack10.aiplayground.rag.service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

/**
 * Parses a {@code filterExpression} string with Spring AI's own {@link FilterExpressionTextParser}
 * (the same parser {@code SearchRequest.filterExpression(String)} uses internally, so validation
 * never disagrees with execution) and validates that every referenced field name is a known,
 * importer-produced metadata field from {@link RagFilterSchema}. Malformed syntax or unknown
 * fields raise an {@link InvalidFilterExpressionException} with an actionable message instead of
 * letting a raw parser exception propagate.
 */
public final class FilterExpressionValidator {

  private static final FilterExpressionTextParser PARSER = new FilterExpressionTextParser();

  private FilterExpressionValidator() {}

  public static Filter.Expression parseAndValidate(String filterExpression) {
    Filter.Expression parsed;
    try {
      parsed = PARSER.parse(filterExpression);
    } catch (RuntimeException e) {
      throw new InvalidFilterExpressionException(
          "Could not parse filterExpression '" + filterExpression + "': " + e.getMessage() + " " + helpSuffix());
    }

    Set<String> referenced = new HashSet<>();
    collectKeys(parsed, referenced);
    Set<String> unknown = referenced.stream()
        .filter(field -> !RagFilterSchema.fieldNames().contains(field))
        .collect(Collectors.toCollection(HashSet::new));

    if (!unknown.isEmpty()) {
      throw new InvalidFilterExpressionException(
          "Unknown filter field(s) " + unknown + " in filterExpression '" + filterExpression + "'. " + helpSuffix());
    }
    return parsed;
  }

  private static String helpSuffix() {
    return "Valid fields are: " + RagFilterSchema.CSV_SUMMARY
        + ". Call getRagFilterSchema for descriptions and example values, e.g. \"source == 'readme.md'\".";
  }

  private static void collectKeys(Filter.Operand operand, Set<String> out) {
    switch (operand) {
      case Filter.Key key -> out.add(key.key());
      case Filter.Expression expr -> {
        collectKeys(expr.left(), out);
        if (expr.right() != null) {
          collectKeys(expr.right(), out);
        }
      }
      case Filter.Group group -> collectKeys(group.content(), out);
      default -> { }
    }
  }
}
