package com.urlshortener.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ShortenRequest {

    @NotBlank(message = "longUrl must not be blank")
    @Pattern(regexp = "^https?://.+", message = "longUrl must start with http:// or https://")
    private String longUrl;

    public ShortenRequest() {
    }

    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }
}
