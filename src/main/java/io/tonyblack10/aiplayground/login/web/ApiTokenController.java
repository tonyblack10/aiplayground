package io.tonyblack10.aiplayground.login.web;

import io.tonyblack10.aiplayground.config.FrontendProperties;
import io.tonyblack10.aiplayground.config.security.JwtService;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/api-token")
@EnableConfigurationProperties(FrontendProperties.class)
public class ApiTokenController {

    private final JwtService jwtService;
    private final FrontendProperties frontendProperties;

    public ApiTokenController(JwtService jwtService, FrontendProperties frontendProperties) {
        this.jwtService = jwtService;
        this.frontendProperties = frontendProperties;
    }

    @GetMapping
    public Mono<String> tokenPage(Authentication authentication, Model model,
                                  ServerWebExchange exchange) {
        String baseUrl = frontendProperties.getBaseUrl();
        if (baseUrl.isBlank()) {
            var uri = exchange.getRequest().getURI();
            baseUrl = uri.getScheme() + "://" + uri.getAuthority();
        }
        model.addAttribute("username", authentication.getName());
        model.addAttribute("authorities", authentication.getAuthorities());
        model.addAttribute("mcpServerUrl", baseUrl + "/mcp");
        return Mono.just("api-token");
    }

    @PostMapping("/generate")
    @ResponseBody
    public Mono<Map<String, String>> generateToken(Authentication authentication) {
        String token = jwtService.generateToken(authentication);
        return Mono.just(Map.of("token", token));
    }
}
