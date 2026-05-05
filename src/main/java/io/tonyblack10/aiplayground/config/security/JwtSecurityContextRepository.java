package io.tonyblack10.aiplayground.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class JwtSecurityContextRepository implements ServerSecurityContextRepository {

    private static final Logger log = LoggerFactory.getLogger(JwtSecurityContextRepository.class);

    private final JwtService jwtService;

    public JwtSecurityContextRepository(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        return Mono.empty();
    }

    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.empty();
        }

        String token = authHeader.substring(7);
        return Mono.fromCallable(() -> {
            Authentication auth = jwtService.getAuthentication(token);
            return (SecurityContext) new SecurityContextImpl(auth);
        }).onErrorResume(e -> {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return Mono.empty();
        });
    }
}
