package io.tonyblack10.aiplayground.config.security;

import java.net.URI;
import io.tonyblack10.aiplayground.config.github.GitHubWebhookProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity(useAuthorizationManager = true)
@EnableConfigurationProperties({UserSecurityProperties.class, JwtProperties.class, GitHubWebhookProperties.class})
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityWebFilterChain mcpSecurityFilterChain(ServerHttpSecurity http,
                                                         JwtService jwtService) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/mcp/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(auth -> auth.anyExchange().authenticated())
                .securityContextRepository(new JwtSecurityContextRepository(jwtService))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityWebFilterChain webSecurityFilterChain(ServerHttpSecurity http,
                                                         AppAuthenticationManager authManager) {
        return http
                // CSRF is disabled — this is a developer playground; enable and configure
                // token propagation via HTMX headers before exposing to untrusted networks.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                .authorizeExchange(auth -> auth
                        .pathMatchers("/login", "/webhooks/**").permitAll()
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
