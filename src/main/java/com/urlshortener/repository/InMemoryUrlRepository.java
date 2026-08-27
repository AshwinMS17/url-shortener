package com.urlshortener.repository;

import com.urlshortener.model.ShortUrl;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Day 1-2 storage: a thread-safe in-memory map, active whenever the
 * "dynamodb" profile is NOT set. Nothing persists across restarts,
 * which is fine while we're just proving the API logic out.
 *
 * On Day 3, DynamoDbUrlRepository will take over when we activate
 * the "dynamodb" profile - the rest of the app won't need to change.
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
}
