package com.postwerk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC configuration — registers custom controller argument resolvers.
 *
 * @since 1.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final OrgContextArgumentResolver orgContextArgumentResolver;

    public WebMvcConfig(OrgContextArgumentResolver orgContextArgumentResolver) {
        this.orgContextArgumentResolver = orgContextArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(orgContextArgumentResolver);
    }
}
