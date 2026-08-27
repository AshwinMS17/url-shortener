package com.urlshortener.service;

import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.repository.UrlRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class UrlService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final UrlRepository repository;
    private final SecureRandom random = new SecureRandom();

    public UrlService(UrlRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates and persists a new short URL for the given long URL and owner.
     * Retries code generation on the rare collision instead of failing outright.
     */
    public ShortUrl createShortUrl(String longUrl, String ownerId) {
        String code = generateUniqueCode();
        ShortUrl shortUrl = new ShortUrl(code, longUrl, ownerId);
        repository.save(shortUrl);
        return shortUrl;
    }

    /**
     * Looks up a short URL by code. Throws UrlNotFoundException if it doesn't exist -
     * the controller/global exception handler turns this into a 404.
     */
    public ShortUrl getByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));
    }

    /**
     * Atomically increments the click count and returns the updated record.
     * Used by the redirect endpoint - the hot path - so the increment is a single
     * storage operation rather than a read-modify-write that could lose counts
     * under concurrent clicks.
     */
    public ShortUrl recordClickAndGet(String code) {
        return repository.incrementClickCountAndGet(code)
                .orElseThrow(() -> new UrlNotFoundException(code));
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!repository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique code after "
                + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
