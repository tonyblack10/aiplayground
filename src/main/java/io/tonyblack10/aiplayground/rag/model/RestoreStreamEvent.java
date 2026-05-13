package io.tonyblack10.aiplayground.rag.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RestoreStreamEvent(
    String type,
    Integer total,
    Integer current,
    Integer restored,
    Integer failed,
    String recordId,
    String source,
    String documentName,
    Integer chunksRestored,
    String error
) {
    public static RestoreStreamEvent started(int total) {
        return new RestoreStreamEvent("started", total, null, null, null, null, null, null, null, null);
    }

    public static RestoreStreamEvent progress(String recordId, String source, String documentName,
        int chunksRestored, String error, int current, int total) {
        return new RestoreStreamEvent("progress", total, current, null, null,
            recordId, source, documentName, chunksRestored, error);
    }

    public static RestoreStreamEvent done(int total, int restored, int failed) {
        return new RestoreStreamEvent("done", total, null, restored, failed,
            null, null, null, null, null);
    }

    public static RestoreStreamEvent cancelled(int processedSoFar) {
        return new RestoreStreamEvent("cancelled", null, processedSoFar, null, null,
            null, null, null, null, null);
    }

    public static RestoreStreamEvent error(String message) {
        return new RestoreStreamEvent("error", null, null, null, null,
            null, null, null, null, message);
    }
}
