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
}
