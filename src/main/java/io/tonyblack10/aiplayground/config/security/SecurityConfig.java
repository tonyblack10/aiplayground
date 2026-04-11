package io.tonyblack10.aiplayground.config.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;

import java.net.URI;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(UserSecurityProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // CSRF is disabled — this is a developer playground; enable and configure
                // token propagation via HTMX headers before exposing to untrusted networks.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                .authorizeExchange(auth -> auth
                        // Public endpoints
                        .pathMatchers("/login").permitAll()

                        // RAG document creation requires CREATE_RAG authority
                        .pathMatchers(HttpMethod.POST, "/rag/*/documents/upload").hasAuthority("CREATE_RAG")
                        .pathMatchers(HttpMethod.POST, "/rag/*/documents/github").hasAuthority("CREATE_RAG")
                        .pathMatchers(HttpMethod.POST, "/rag/*/documents/confluence").hasAuthority("CREATE_RAG")
                        .pathMatchers(HttpMethod.POST, "/rag/*/documents/monday").hasAuthority("CREATE_RAG")
                        .pathMatchers(HttpMethod.POST, "/rag/*/documents/s3").hasAuthority("CREATE_RAG")

                        // RAG document deletion requires DELETE_RAG authority
                        .pathMatchers(HttpMethod.POST, "/rag/*/documents/delete").hasAuthority("DELETE_RAG")

                        // Everything else requires authentication
                        .anyExchange().authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                )

                .logout(logout -> {
                    RedirectServerLogoutSuccessHandler handler = new RedirectServerLogoutSuccessHandler();
                    handler.setLogoutSuccessUrl(URI.create("/login?logout"));
                    logout.logoutUrl("/logout").logoutSuccessHandler(handler);
                })

                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
