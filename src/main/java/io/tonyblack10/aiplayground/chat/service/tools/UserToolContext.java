package io.tonyblack10.aiplayground.chat.service.tools;

import java.util.Collection;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public record UserToolContext(String username, Collection<String> authorities) {

    public static final String TOOL_CONTEXT_KEY = "user";

    public static UserToolContext from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return new UserToolContext(
            authentication.getName(),
            authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList()
        );
    }
}
