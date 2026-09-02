package io.tonyblack10.aiplayground.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.tonyblack10.aiplayground.rag.model.DocumentEntry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;
import reactor.core.publisher.Mono;

class RagAggregationServiceTest {

  private final DocumentManagementService documentManagementService = mock(DocumentManagementService.class);
  private final RagAggregationService service = new RagAggregationService(documentManagementService);

  private static DocumentEntry entry(String id, Map<String, Object> metadata) {
    return new DocumentEntry(id, String.valueOf(metadata.get("source")), "preview", "storeId",
        LocalDateTime.now(), metadata);
  }

  @Test
  void groupsAndCountsBySourceField() {
    List<DocumentEntry> entries = List.of(
        entry("1", Map.of("source", "confluence")),
        entry("2", Map.of("source", "confluence")),
        entry("3", Map.of("source", "monday")));
    when(documentManagementService.listDocuments("storeId")).thenReturn(Mono.just(entries));

    List<RagAggregationService.AggregateBucket> result = service.aggregate("storeId", "source", null).block();

    assertThat(result).extracting(RagAggregationService.AggregateBucket::value, RagAggregationService.AggregateBucket::count)
        .containsExactlyInAnyOrder(tuple("confluence", 2L), tuple("monday", 1L));
  }

  @Test
  void appliesFilterExpressionBeforeGrouping() {
    List<DocumentEntry> entries = List.of(
        entry("1", Map.of("source", "confluence", "spaceKey", "ENG")),
        entry("2", Map.of("source", "confluence", "spaceKey", "OPS")),
        entry("3", Map.of("source", "monday")));
    when(documentManagementService.listDocuments("storeId")).thenReturn(Mono.just(entries));

    Filter.Expression filter = FilterExpressionValidator.parseAndValidate("spaceKey == 'ENG'");
    List<RagAggregationService.AggregateBucket> result =
        service.aggregate("storeId", "source", filter).block();

    assertThat(result).containsExactly(new RagAggregationService.AggregateBucket("confluence", 1L));
  }

  @Test
  void missingFieldGroupsUnderMissingLabel() {
    List<DocumentEntry> entries = List.of(entry("1", Map.of("source", "readme.md")));
    when(documentManagementService.listDocuments("storeId")).thenReturn(Mono.just(entries));

    List<RagAggregationService.AggregateBucket> result = service.aggregate("storeId", "spaceKey", null).block();

    assertThat(result).containsExactly(new RagAggregationService.AggregateBucket("(missing)", 1L));
  }
}
