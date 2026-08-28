package com.urlshortener.security;

import com.urlshortener.config.ApiKeyProperties;
import com.urlshortener.exception.ApiKeyUnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces the {@code X-API-Key} header on the endpoints it is registered for
 * (see {@code WebConfig}). On success the resolved owner id is stashed as a
 * request attribute for the controller to read; on failure it throws
 * {@link ApiKeyUnauthorizedException}, which the global handler renders as 401.
 */
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-API-Key";

    /** Request attribute holding the authenticated owner id. */
    public static final String OWNER_ATTRIBUTE = "apiKeyOwnerId";

    private final ApiKeyProperties properties;

    public ApiKeyAuthInterceptor(ApiKeyProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String presented = request.getHeader(HEADER);
        String ownerId = properties.ownerForPresentedKey(presented)
                .orElseThrow(() -> new ApiKeyUnauthorizedException(
                        presented == null || presented.isBlank()
                                ? "Missing " + HEADER + " header"
                                : "Invalid API key"));
        request.setAttribute(OWNER_ATTRIBUTE, ownerId);
        return true;
    }
}
