package com.urlshortener.repository;

import com.urlshortener.model.ShortUrl;

import java.util.Optional;

/**
 * Abstraction over storage so the service layer doesn't care whether
 * records live in memory (Day 1-2) or DynamoDB (Day 3+).
 */
public interface UrlRepository {

    void save(ShortUrl shortUrl);

    Optional<ShortUrl> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Atomically increments the click counter for the given code and returns the
     * updated record, or {@link Optional#empty()} if no such code exists.
     *
     * <p>This is a single operation on purpose: the redirect endpoint is the hot
     * path and a read-then-write would race under concurrent clicks. The DynamoDB
     * implementation pushes the increment down into an update expression so the
     * database does the arithmetic.
     */
    Optional<ShortUrl> incrementClickCountAndGet(String code);
}
