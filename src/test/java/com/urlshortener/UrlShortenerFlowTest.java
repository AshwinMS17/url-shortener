package com.urlshortener;

import com.urlshortener.model.ShortUrl;
import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UrlShortenerFlowTest {

    @Autowired
    private UrlService urlService;

    @Test
    void createLookupAndClickFlow() {
        ShortUrl created = urlService.createShortUrl("https://example.com/very/long/path", "test-user");

        assertNotNull(created.getCode());
        assertEquals(6, created.getCode().length());
        assertEquals(0, created.getClickCount());

        ShortUrl fetched = urlService.getByCode(created.getCode());
        assertEquals(created.getLongUrl(), fetched.getLongUrl());

        urlService.recordClickAndGet(created.getCode());
        urlService.recordClickAndGet(created.getCode());
        ShortUrl afterClicks = urlService.getByCode(created.getCode());

        assertEquals(2, afterClicks.getClickCount());
    }

    @Test
    void unknownCodeThrows() {
        assertThrows(
                com.urlshortener.exception.UrlNotFoundException.class,
                () -> urlService.getByCode("doesNotExist")
        );
    }
}
