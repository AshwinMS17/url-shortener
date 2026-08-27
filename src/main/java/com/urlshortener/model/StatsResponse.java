package com.urlshortener.model;

import java.time.Instant;

public record StatsResponse(String code, String longUrl, long clickCount, Instant createdAt) {
}
