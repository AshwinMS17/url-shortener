package com.urlshortener.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single shortened URL record.
 *
 * Kept as a plain POJO for the Day 1-2 in-memory version.
 * On Day 3 we'll add @DynamoDbBean / @DynamoDbPartitionKey annotations
 * to make this persistable to DynamoDB without changing the shape much.
 */
public class ShortUrl {

    private String code;        // the short code, e.g. "aZ3kP9" - will be the partition key
    private String longUrl;     // the original URL being shortened
    private String ownerId;     // which API key/user created this - added Day 4
    private Instant createdAt;
    private AtomicLong clickCount = new AtomicLong(0);

    public ShortUrl() {
    }

    public ShortUrl(String code, String longUrl, String ownerId) {
        this.code = code;
        this.longUrl = longUrl;
        this.ownerId = ownerId;
        this.createdAt = Instant.now();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public long getClickCount() {
        return clickCount.get();
    }

    public void setClickCount(long count) {
        this.clickCount.set(count);
    }

    public long incrementClickCount() {
        return clickCount.incrementAndGet();
    }
}
