package io.tonyblack10.aiplayground.config.github;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.github")
public class GitHubWebhookProperties {

    private List<WebhookConfig> webhooks = new ArrayList<>();

    public List<WebhookConfig> getWebhooks() {
        return webhooks;
    }

    public void setWebhooks(List<WebhookConfig> webhooks) {
        this.webhooks = webhooks;
    }

    public static class WebhookConfig {

        private String repoUrl;
        private String directory = "";
        private String branch = "main";
        private String storeId;
        private String secret;

        public String getRepoUrl() {
            return repoUrl;
        }

        public void setRepoUrl(String repoUrl) {
            this.repoUrl = repoUrl;
        }

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory != null ? directory : "";
        }

        public String getBranch() {
            return branch;
        }

        public void setBranch(String branch) {
            this.branch = branch;
        }

        public String getStoreId() {
            return storeId;
        }

        public void setStoreId(String storeId) {
            this.storeId = storeId;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        /** Derives "owner/repo" from https://github.com/owner/repo[.git] */
        public String fullName() {
            if (repoUrl == null) return "";
            String normalized = repoUrl.trim().replaceAll("/$", "").replace(".git", "");
            int idx = normalized.indexOf("github.com/");
            return idx >= 0 ? normalized.substring(idx + "github.com/".length()) : normalized;
        }
    }
}
