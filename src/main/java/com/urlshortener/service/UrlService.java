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
     * Looks up a short URL and increments its click count in one step.
     * Used by the redirect endpoint.
     */
    public ShortUrl recordClickAndGet(String code) {
        ShortUrl shortUrl = getByCode(code);
        shortUrl.incrementClickCount();
        repository.save(shortUrl); // no-op for in-memory map, matters once DynamoDB is real storage
        return shortUrl;
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
