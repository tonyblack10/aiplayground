package io.tonyblack10.aiplayground.config.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Enforces per-RAG-store access control on controller methods that receive a
 * {@code storeId} path variable.
 *
 * <p>A user passes the check when they hold either:
 * <ul>
 *   <li>{@code RAG_ACCESS_ALL} — unrestricted access to every store, or</li>
 *   <li>{@code RAG_ACCESS_{SUFFIX}} — access to that specific store
 *       (e.g. {@code RAG_ACCESS_SIMPLE}, {@code RAG_ACCESS_PGVECTOR},
 *       {@code RAG_ACCESS_REDIS}).</li>
 * </ul>
 *
 * <p>This annotation must be placed on a method whose signature includes a
 * parameter named {@code storeId}, as that name is referenced in the SpEL
 * expression via {@link RagAuthorityHelper#authorityFor(String)}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@ragAuthorityHelper.hasAccess(authentication, #storeId)")
public @interface RequiresRagAccess {}
