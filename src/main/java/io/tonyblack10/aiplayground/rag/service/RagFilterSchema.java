package io.tonyblack10.aiplayground.rag.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Static catalog of the metadata fields actually written by this application's document
 * importers, used to validate {@code filterExpression} strings before executing a search and
 * to let callers discover valid fields via {@code getRagFilterSchema}.
 *
 * <p>Ad hoc JSON-folder ingestion carries free-form metadata and is intentionally not
 * enumerated here — this catalog covers only the fixed, code-defined importers (manual upload,
 * GitHub, Confluence, Monday.com, S3, URL links).
 */
public final class RagFilterSchema {

  public record FieldDef(String name, String description, String exampleValue) {}

  private static final List<FieldDef> FIELDS = List.of(
      new FieldDef("source",
          "Origin of the chunk: the filename for uploads/GitHub/S3/URL imports, "
              + "or the literal 'confluence'/'monday' for those importers",
          "readme.md"),
      new FieldDef("spaceKey", "Confluence space key (Confluence-imported documents only)", "ENG"),
      new FieldDef("pageId", "Confluence page id (Confluence-imported documents only)", "123456"),
      new FieldDef("title", "Confluence page title (Confluence-imported documents only)", "Release Checklist"),
      new FieldDef("boardId", "Monday.com board id (Monday-imported documents only)", "789012"),
      new FieldDef("itemId", "Monday.com item id (Monday-imported documents only)", "345678"),
      new FieldDef("itemName", "Monday.com item name (Monday-imported documents only)", "Sprint 42 planning"),
      new FieldDef("groupId", "Monday.com group id (Monday-imported documents only)", "topics"),
      new FieldDef("groupTitle", "Monday.com group title (Monday-imported documents only)", "In Progress")
  );

  /** Comma-separated field names, embeddable in a compile-time-constant {@code @Tool} description. */
  public static final String CSV_SUMMARY =
      "source, spaceKey, pageId, title, boardId, itemId, itemName, groupId, groupTitle";

  private RagFilterSchema() {}

  public static List<FieldDef> fields() {
    return FIELDS;
  }

  public static Set<String> fieldNames() {
    return FIELDS.stream().map(FieldDef::name).collect(Collectors.toUnmodifiableSet());
  }
}
