package io.tonyblack10.aiplayground.rag.service;

import io.tonyblack10.aiplayground.rag.model.DocumentEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Counts/groups already-ingested documents by a metadata field, using the in-memory
 * {@link DocumentRegistry} (via {@link DocumentManagementService#listDocuments}) rather than a
 * vector similarity search — vector stores here aren't built for aggregation, so quantitative
 * questions ("how many docs from Confluence") are answered from the registry instead.
 *
 * <p>Note this is per-JVM-instance state: counts reflect only documents ingested into this
 * running instance's registry, not a live/shared count across multiple app instances.
 */
@Service
public class RagAggregationService {

  private static final String MISSING_VALUE_LABEL = "(missing)";

  private final DocumentManagementService documentManagementService;

  public RagAggregationService(DocumentManagementService documentManagementService) {
    this.documentManagementService = documentManagementService;
  }

  public record AggregateBucket(String value, long count) {}

  public Mono<List<AggregateBucket>> aggregate(String storeId, String groupByField, Filter.Expression filterExpression) {
    return documentManagementService.listDocuments(storeId)
        .map(entries -> entries.stream()
            .filter(entry -> filterExpression == null || matches(filterExpression, entry.metadata()))
            .collect(Collectors.groupingBy(entry -> valueOf(entry, groupByField), Collectors.counting()))
            .entrySet().stream()
            .map(e -> new AggregateBucket(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingLong(AggregateBucket::count).reversed())
            .toList());
  }

  private String valueOf(DocumentEntry entry, String field) {
    Object value = entry.metadata() != null ? entry.metadata().get(field) : null;
    return value != null ? String.valueOf(value) : MISSING_VALUE_LABEL;
  }

  private boolean matches(Filter.Expression filterExpression, Map<String, Object> metadata) {
    return evaluate(filterExpression, metadata);
  }

  private boolean evaluate(Filter.Expression expr, Map<String, Object> metadata) {
    return switch (expr.type()) {
      case AND -> evaluateOperand(expr.left(), metadata) && evaluateOperand(expr.right(), metadata);
      case OR -> evaluateOperand(expr.left(), metadata) || evaluateOperand(expr.right(), metadata);
      case NOT -> !evaluateOperand(expr.left(), metadata);
      default -> evaluateComparison(expr, metadata);
    };
  }

  private boolean evaluateOperand(Filter.Operand operand, Map<String, Object> metadata) {
    return switch (operand) {
      case Filter.Expression expr -> evaluate(expr, metadata);
      case Filter.Group group -> evaluate(group.content(), metadata);
      default -> throw new IllegalStateException("Expected a boolean sub-expression, got: " + operand);
    };
  }

  private boolean evaluateComparison(Filter.Expression expr, Map<String, Object> metadata) {
    String field = ((Filter.Key) expr.left()).key();
    Object actual = metadata != null ? metadata.get(field) : null;
    return switch (expr.type()) {
      case ISNULL -> actual == null;
      case ISNOTNULL -> actual != null;
      case EQ -> actual != null && String.valueOf(actual).equals(String.valueOf(valueOf(expr.right())));
      case NE -> actual == null || !String.valueOf(actual).equals(String.valueOf(valueOf(expr.right())));
      case IN -> actual != null && asValueList(expr.right()).contains(String.valueOf(actual));
      case NIN -> actual == null || !asValueList(expr.right()).contains(String.valueOf(actual));
      case GT, GTE, LT, LTE -> compareNumeric(actual, valueOf(expr.right()), expr.type());
      default -> throw new IllegalStateException("Unsupported filter comparison: " + expr.type());
    };
  }

  private Object valueOf(Filter.Operand operand) {
    return ((Filter.Value) operand).value();
  }

  private List<String> asValueList(Filter.Operand operand) {
    Object value = valueOf(operand);
    if (value instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of(String.valueOf(value));
  }

  private boolean compareNumeric(Object actual, Object expected, Filter.ExpressionType type) {
    if (actual == null) {
      return false;
    }
    double actualValue;
    double expectedValue;
    try {
      actualValue = Double.parseDouble(String.valueOf(actual));
      expectedValue = Double.parseDouble(String.valueOf(expected));
    } catch (NumberFormatException e) {
      return false;
    }
    return switch (type) {
      case GT -> actualValue > expectedValue;
      case GTE -> actualValue >= expectedValue;
      case LT -> actualValue < expectedValue;
      case LTE -> actualValue <= expectedValue;
      default -> false;
    };
  }
}
