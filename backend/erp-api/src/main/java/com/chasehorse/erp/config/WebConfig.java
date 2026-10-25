package com.chasehorse.erp.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.chasehorse.erp.security.CurrentActorResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final CurrentActorResolver resolver;

    public WebConfig(CurrentActorResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
    }
}
