package io.tonyblack10.aiplayground.config.security;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity(useAuthorizationManager = true)
@EnableConfigurationProperties(UserSecurityProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         AppAuthenticationManager authManager) {
        return http
                // CSRF is disabled — this is a developer playground; enable and configure
                // token propagation via HTMX headers before exposing to untrusted networks.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                .authorizeExchange(auth -> auth
                        // Public endpoints
                        .pathMatchers("/login").permitAll()
                        // MCP server endpoint — accessible to programmatic clients
                        .pathMatchers("/mcp/**").permitAll()

                        // Per-RAG-store access is enforced at the method level via @RequiresRagAccess
                        .anyExchange().authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .authenticationManager(authManager)
                        .authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler("/chat"))
                )

                .logout(logout -> {
                    RedirectServerLogoutSuccessHandler handler = new RedirectServerLogoutSuccessHandler();
                    handler.setLogoutSuccessUrl(URI.create("/login?logout"));
                    logout.logoutUrl("/logout").logoutSuccessHandler(handler);
                })

                .build();
    }
}
