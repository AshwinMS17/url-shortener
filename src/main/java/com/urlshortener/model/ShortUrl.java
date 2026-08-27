package com.urlshortener.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single shortened URL record.
 *
 * The @DynamoDbBean annotations let the enhanced client map this straight to a
 * DynamoDB item (partition key = "code"). The in-memory repository ignores them
 * and just stores the object as-is, so the same class serves both profiles.
 *
 * clickCount stays an AtomicLong so the in-memory path can increment it safely;
 * the enhanced client only sees the long getter/setter pair and maps it as a Number.
 */
@DynamoDbBean
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

    @DynamoDbPartitionKey
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
