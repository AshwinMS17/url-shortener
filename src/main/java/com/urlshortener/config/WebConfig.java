package com.urlshortener.config;

import com.urlshortener.security.ApiKeyAuthInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the API-key interceptor. Only {@code POST /shorten} is protected;
 * the redirect and stats endpoints stay public so short links work for anyone.
 */
@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final ApiKeyProperties apiKeyProperties;

    public WebConfig(ApiKeyProperties apiKeyProperties) {
        this.apiKeyProperties = apiKeyProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiKeyAuthInterceptor(apiKeyProperties))
                .addPathPatterns("/shorten");
    }
}
