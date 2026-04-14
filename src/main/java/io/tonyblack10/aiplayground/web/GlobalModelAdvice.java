package io.tonyblack10.aiplayground.web;

import io.tonyblack10.aiplayground.config.FrontendProperties;
import io.tonyblack10.aiplayground.config.security.RagAuthorityHelper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.Authentication;
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
    private final RagAuthorityHelper ragAuthorityHelper;

    public GlobalModelAdvice(FrontendProperties frontendProperties,
                             RagAuthorityHelper ragAuthorityHelper) {
        this.frontendProperties = frontendProperties;
        this.ragAuthorityHelper = ragAuthorityHelper;
    }

    @ModelAttribute
    public void addGlobalAttributes(Model model, Authentication authentication) {
        model.addAttribute("frontendBaseUrl", frontendProperties.getBaseUrl());
        model.addAttribute("hasAnyRagAccess", ragAuthorityHelper.hasAnyRagAccess(authentication));
    }
}
