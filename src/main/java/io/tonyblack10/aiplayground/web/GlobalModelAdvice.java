package io.tonyblack10.aiplayground.web;

import io.tonyblack10.aiplayground.config.FrontendProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes global model attributes to every Thymeleaf view.
 */
@ControllerAdvice
@EnableConfigurationProperties(FrontendProperties.class)
public class GlobalModelAdvice {

    private final FrontendProperties frontendProperties;

    public GlobalModelAdvice(FrontendProperties frontendProperties) {
        this.frontendProperties = frontendProperties;
    }

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        model.addAttribute("frontendBaseUrl", frontendProperties.getBaseUrl());
    }
}
