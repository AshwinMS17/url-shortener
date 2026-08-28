package com.urlshortener.repository;

import com.urlshortener.model.ShortUrl;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default storage: a thread-safe in-memory map, active whenever the
 * "dynamodb" profile is NOT set. Nothing persists across restarts,
 * which is fine for local development and the API-logic tests.
 *
 * {@link DynamoDbUrlRepository} takes over when the "dynamodb" profile is
 * active - the service layer depends only on {@link UrlRepository}, so
 * nothing else changes.
 */
@Repository
@Profile("!dynamodb")
public class InMemoryUrlRepository implements UrlRepository {

    private final Map<String, ShortUrl> store = new ConcurrentHashMap<>();

    @Override
    public void save(ShortUrl shortUrl) {
        store.put(shortUrl.getCode(), shortUrl);
    }

    @Override
    public Optional<ShortUrl> findByCode(String code) {
        return Optional.ofNullable(store.get(code));
    }

    @Override
    public boolean existsByCode(String code) {
        return store.containsKey(code);
    }

    @Override
    public Optional<ShortUrl> incrementClickCountAndGet(String code) {
        // ConcurrentHashMap.compute runs atomically for the key; the ShortUrl's
        // own AtomicLong keeps the increment safe even without that guarantee.
        ShortUrl updated = store.computeIfPresent(code, (key, shortUrl) -> {
            shortUrl.incrementClickCount();
            return shortUrl;
        });
        return Optional.ofNullable(updated);
    }
}
