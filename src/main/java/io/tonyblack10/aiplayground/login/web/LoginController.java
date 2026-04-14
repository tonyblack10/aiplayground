package io.tonyblack10.aiplayground.login.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Controller
public class LoginController {

    @GetMapping("/login")
    public Mono<String> login(ServerWebExchange exchange, Model model) {
        var params = exchange.getRequest().getQueryParams();
        model.addAttribute("loginError", params.containsKey("error"));
        model.addAttribute("logoutSuccess", params.containsKey("logout"));
        return Mono.just("login");
    }
}
