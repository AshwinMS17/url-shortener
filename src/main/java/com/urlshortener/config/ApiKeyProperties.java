package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Binds {@code app.api-keys} - a map of {@code <owner-id>: <secret>} for the
 * clients allowed to create short URLs.
 *
 * <p>Fail-closed: if nothing is configured, no key can authenticate and every
 * {@code POST /shorten} is rejected. Real environments inject these from a
 * mounted Secret (Day 5); {@code application.yml} ships one throwaway local entry.
 */
@ConfigurationProperties(prefix = "app")
public class ApiKeyProperties {

    /** Owner id -> secret API key. */
    private Map<String, String> apiKeys = new LinkedHashMap<>();

    public Map<String, String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(Map<String, String> apiKeys) {
        this.apiKeys = apiKeys;
    }

    /**
     * Returns the owner id whose secret matches the presented key, or empty if
     * none does. The comparison runs against every configured secret without an
     * early exit so lookup time does not leak which prefix was correct.
     */
    public Optional<String> ownerForPresentedKey(String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank()) {
            return Optional.empty();
        }
        byte[] presented = presentedKey.getBytes(StandardCharsets.UTF_8);
        String matchedOwner = null;
        for (Map.Entry<String, String> entry : apiKeys.entrySet()) {
            byte[] secret = entry.getValue().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(secret, presented)) {
                matchedOwner = entry.getKey();
            }
        }
        return Optional.ofNullable(matchedOwner);
    }
}
