package com.urlshortener.model;

public record ShortenResponse(String code, String shortUrl, String longUrl) {
}
