package io.tonyblack10.aiplayground.config.security;

import io.tonyblack10.aiplayground.rag.model.VectorStoreInfo;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Central helper for RAG store authority resolution.
 *
 * <p>Maps store bean IDs to clean authority name suffixes and provides
 * access-check utilities used both in {@code @PreAuthorize} SpEL expressions
 * and in controller filtering logic.
 *
 * <p>Authority format: {@code RAG_ACCESS_ALL} for unrestricted access, or
 * {@code RAG_ACCESS_{SUFFIX}} for a specific store, where {@code {SUFFIX}} is
 * the value returned by {@link #authorityFor(String)}.
 */
@Component("ragAuthorityHelper")
public class RagAuthorityHelper {

    private static final String ACCESS_ALL = "RAG_ACCESS_ALL";
    private static final String ACCESS_PREFIX = "RAG_ACCESS_";

    private static final Map<String, String> SUFFIX_BY_STORE_ID = Map.of(
        "simpleVectorStore", "SIMPLE",
        "pgVectorStore",     "PGVECTOR",
        "redisVectorStore",  "REDIS"
    );

    /**
     * Returns the authority suffix for a given store bean ID.
     * e.g. {@code "simpleVectorStore"} → {@code "SIMPLE"} (used in {@code "RAG_ACCESS_SIMPLE"}).
     */
    public String authorityFor(String storeId) {
        return SUFFIX_BY_STORE_ID.getOrDefault(storeId, storeId.toUpperCase());
    }

    /**
     * Returns {@code true} if the user holds {@code RAG_ACCESS_ALL} or
     * {@code RAG_ACCESS_{suffix}} for the given store.
     * Used in {@code @PreAuthorize} SpEL via {@code @ragAuthorityHelper.hasAccess(...)}.
     */
    public boolean hasAccess(Authentication authentication, String storeId) {
        if (authentication == null) return false;
        String storeAuthority = ACCESS_PREFIX + authorityFor(storeId);
        return hasAuthority(authentication, ACCESS_ALL)
            || hasAuthority(authentication, storeAuthority);
    }

    /**
     * Returns {@code true} if the user holds at least one {@code RAG_ACCESS_*} authority.
     * Used to gate access to the RAG index page and the RAG menu entry.
     * Used in {@code @PreAuthorize} SpEL via {@code @ragAuthorityHelper.hasAnyRagAccess(...)}.
     */
    public boolean hasAnyRagAccess(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().startsWith(ACCESS_PREFIX));
    }

    /**
     * Filters a list of stores to only those accessible to the given user.
     */
    public List<VectorStoreInfo> accessibleStores(Authentication authentication,
                                                   List<VectorStoreInfo> stores) {
        return stores.stream()
            .filter(store -> hasAccess(authentication, store.id()))
            .toList();
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(authority));
    }
}
