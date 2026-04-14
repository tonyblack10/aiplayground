package io.tonyblack10.aiplayground.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Frontend configuration properties.
 *
 * <p>Example configuration:
 * <pre>
 * app:
 *   frontend:
 *     base-url: https://myapp.example.com
 * </pre>
 *
 * <p>When {@code base-url} is empty (the default), all HTMX requests use
 * relative paths, which is correct for same-origin deployments.
 * Set it to an absolute URL when the frontend is served behind a reverse proxy
 * or on a different origin from the backend.
 */
@ConfigurationProperties(prefix = "app.frontend")
public class FrontendProperties {

    /** Base URL used by the frontend to reach the backend (e.g. https://myapp.example.com). */
    private String baseUrl = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl.stripTrailing().replaceAll("/+$", "") : "";
    }
}
