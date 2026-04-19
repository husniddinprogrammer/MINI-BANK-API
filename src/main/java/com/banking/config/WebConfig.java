package com.banking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Caps pageable max page size globally.
 * Needed because @EnableSpringDataWebSupport bypasses Spring Boot's
 * spring.data.web.pageable.* auto-configuration.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();
        resolver.setMaxPageSize(MAX_PAGE_SIZE);
        resolver.setFallbackPageable(org.springframework.data.domain.PageRequest.of(0, 10));
        resolvers.add(resolver);
    }
}
