package com.urlshortener.controller;

import com.urlshortener.model.ShortUrl;
import com.urlshortener.model.ShortenRequest;
import com.urlshortener.model.ShortenResponse;
import com.urlshortener.model.StatsResponse;
import com.urlshortener.security.ApiKeyAuthInterceptor;
import com.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class UrlController {

    private final UrlService urlService;
    private final String baseUrl;

    public UrlController(UrlService urlService, @Value("${app.base-url}") String baseUrl) {
        this.urlService = urlService;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(
            @Valid @RequestBody ShortenRequest request,
            @RequestAttribute(ApiKeyAuthInterceptor.OWNER_ATTRIBUTE) String ownerId) {

        // ownerId is set by ApiKeyAuthInterceptor after it validates the X-API-Key header,
        // so by the time we get here the caller is authenticated.
        ShortUrl created = urlService.createShortUrl(request.getLongUrl(), ownerId);

        ShortenResponse response = new ShortenResponse(
                created.getCode(),
                baseUrl + "/" + created.getCode(),
                created.getLongUrl()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        ShortUrl shortUrl = urlService.recordClickAndGet(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, shortUrl.getLongUrl())
                .build();
    }

    @GetMapping("/{code}/stats")
    public ResponseEntity<StatsResponse> stats(@PathVariable String code) {
        ShortUrl shortUrl = urlService.getByCode(code);
        StatsResponse response = new StatsResponse(
                shortUrl.getCode(),
                shortUrl.getLongUrl(),
                shortUrl.getClickCount(),
                shortUrl.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }
}
