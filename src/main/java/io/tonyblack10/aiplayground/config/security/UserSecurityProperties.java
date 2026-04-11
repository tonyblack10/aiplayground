package io.tonyblack10.aiplayground.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binds user security properties from application configuration.
 *
 * <p>Example configuration:
 * <pre>
 * app:
 *   user:
 *     accounts:
 *       user01: "{noop}pass01"
 *     permissions:
 *       CREATE_RAG:
 *         - user01
 *         - user02
 *       DELETE_RAG:
 *         - user03
 *       # Roles listed here are granted to ALL authenticated users.
 *       # Their entry in the permissions map above is ignored.
 *       all:
 *         - CREATE_RAG
 * </pre>
 */
@ConfigurationProperties(prefix = "app.user")
public class UserSecurityProperties {

    /** Maps username to encoded password (e.g. "{noop}secret" or "{bcrypt}..."). */
    private Map<String, String> accounts = new LinkedHashMap<>();

    /**
     * Maps role name to the list of usernames that hold that role.
     * The special key {@code all} holds a list of roles granted to every user.
     * If a role appears under {@code all}, its per-user mapping is ignored.
     */
    private Map<String, List<String>> permissions = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Convenience helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the roles granted to every authenticated user regardless of
     * individual assignments (driven by {@code app.user.permissions.all}).
     */
    public List<String> globalRoles() {
        return permissions.getOrDefault("all", List.of());
    }

    /**
     * Returns all roles that should be assigned to the given user, combining
     * the global roles ({@code all}) with per-user role assignments.
     *
     * <p>If a role is present in {@code all}, its individual assignment list
     * in the permissions map is ignored — all users already have it.
     */
    public List<String> rolesForUser(String username) {
        List<String> global = new ArrayList<>(globalRoles());

        permissions.entrySet().stream()
                .filter(e -> !"all".equals(e.getKey()))
                .filter(e -> !global.contains(e.getKey()))
                .filter(e -> e.getValue().stream().anyMatch(u -> u.strip().equals(username.strip())))
                .map(Map.Entry::getKey)
                .forEach(global::add);

        return global;
    }

    // -------------------------------------------------------------------------
    // Getters / setters (required for @ConfigurationProperties binding)
    // -------------------------------------------------------------------------

    public Map<String, String> getAccounts() {
        return accounts;
    }

    public void setAccounts(Map<String, String> accounts) {
        this.accounts = accounts;
    }

    public Map<String, List<String>> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, List<String>> permissions) {
        this.permissions = permissions;
    }
}
