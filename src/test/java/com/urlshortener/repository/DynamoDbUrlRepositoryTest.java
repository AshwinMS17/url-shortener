package com.urlshortener.repository;

import com.urlshortener.model.ShortUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;

/**
 * Exercises {@link DynamoDbUrlRepository} against a real DynamoDB API served by
 * LocalStack in a container. Self-skips when Docker is not available, so it is
 * safe to leave in the default {@code mvn verify} run.
 */
@SpringBootTest
@ActiveProfiles("dynamodb")
@Testcontainers(disabledWithoutDocker = true)
class DynamoDbUrlRepositoryTest {

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.5"))
            .withServices(DYNAMODB);

    @DynamicPropertySource
    static void dynamoProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.dynamodb.endpoint", () -> LOCALSTACK.getEndpointOverride(DYNAMODB).toString());
        registry.add("aws.dynamodb.region", LOCALSTACK::getRegion);
        registry.add("aws.dynamodb.access-key", LOCALSTACK::getAccessKey);
        registry.add("aws.dynamodb.secret-key", LOCALSTACK::getSecretKey);
        registry.add("aws.dynamodb.table-name", () -> "url-shortener-test");
    }

    @Autowired
    UrlRepository repository;

    @Autowired
    DynamoDbTable<ShortUrl> table;

    @Autowired
    DynamoDbClient client;

    @BeforeEach
    void createTable() {
        table.createTable();
        client.waiter().waitUntilTableExists(b -> b.tableName("url-shortener-test"));
    }

    @AfterEach
    void dropTable() {
        client.deleteTable(b -> b.tableName("url-shortener-test"));
        client.waiter().waitUntilTableNotExists(b -> b.tableName("url-shortener-test"));
    }

    @Test
    void wiresTheDynamoDbImplementation() {
        assertInstanceOf(DynamoDbUrlRepository.class, repository);
    }

    @Test
    void savesAndReadsBackARecord() {
        ShortUrl saved = new ShortUrl("abc123", "https://example.com/page", "owner-1");
        repository.save(saved);

        ShortUrl found = repository.findByCode("abc123").orElseThrow();
        assertEquals("abc123", found.getCode());
        assertEquals("https://example.com/page", found.getLongUrl());
        assertEquals("owner-1", found.getOwnerId());
        assertEquals(0, found.getClickCount());
        assertEquals(saved.getCreatedAt(), found.getCreatedAt());

        assertTrue(repository.existsByCode("abc123"));
    }

    @Test
    void missingCodeReadsAsEmpty() {
        assertTrue(repository.findByCode("nope").isEmpty());
        assertFalse(repository.existsByCode("nope"));
        assertTrue(repository.incrementClickCountAndGet("nope").isEmpty());
    }

    @Test
    void incrementReturnsUpdatedRecordAndPreservesOtherFields() {
        repository.save(new ShortUrl("hit001", "https://example.com/target", "owner-2"));

        ShortUrl afterFirst = repository.incrementClickCountAndGet("hit001").orElseThrow();
        assertEquals(1, afterFirst.getClickCount());
        assertEquals("https://example.com/target", afterFirst.getLongUrl());
        assertEquals("owner-2", afterFirst.getOwnerId());

        ShortUrl afterSecond = repository.incrementClickCountAndGet("hit001").orElseThrow();
        assertEquals(2, afterSecond.getClickCount());

        assertEquals(2, repository.findByCode("hit001").orElseThrow().getClickCount());
    }

    @Test
    void concurrentIncrementsDoNotLoseCounts() throws Exception {
        repository.save(new ShortUrl("race01", "https://example.com/race", "owner-3"));

        int clicks = 50;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            var futures = IntStream.range(0, clicks)
                    .mapToObj(i -> pool.submit(() -> repository.incrementClickCountAndGet("race01")))
                    .toList();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }

        assertEquals(clicks, repository.findByCode("race01").orElseThrow().getClickCount());
    }
}
