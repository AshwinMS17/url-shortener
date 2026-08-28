package com.urlshortener.repository;

import com.urlshortener.model.ShortUrl;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB-backed storage, active under the "dynamodb" profile.
 *
 * <p>Reads and writes of whole records go through the enhanced client
 * ({@link DynamoDbTable}); the click counter uses a low-level update expression
 * so the increment is atomic on the server rather than a racy read-modify-write.
 */
@Repository
@Profile("dynamodb")
public class DynamoDbUrlRepository implements UrlRepository {

    private final DynamoDbTable<ShortUrl> table;
    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbUrlRepository(DynamoDbTable<ShortUrl> table, DynamoDbClient client) {
        this.table = table;
        this.client = client;
        this.tableName = table.tableName();
    }

    @Override
    public void save(ShortUrl shortUrl) {
        table.putItem(shortUrl);
    }

    @Override
    public Optional<ShortUrl> findByCode(String code) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(code).build()));
    }

    @Override
    public boolean existsByCode(String code) {
        return findByCode(code).isPresent();
    }

    @Override
    public Optional<ShortUrl> incrementClickCountAndGet(String code) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("code", AttributeValue.fromS(code)))
                .updateExpression("SET #cc = if_not_exists(#cc, :zero) + :one")
                .conditionExpression("attribute_exists(#c)")
                .expressionAttributeNames(Map.of("#cc", "clickCount", "#c", "code"))
                .expressionAttributeValues(Map.of(
                        ":one", AttributeValue.fromN("1"),
                        ":zero", AttributeValue.fromN("0")))
                .returnValues(ReturnValue.ALL_NEW)
                .build();

        try {
            Map<String, AttributeValue> updated = client.updateItem(request).attributes();
            return Optional.of(table.tableSchema().mapToItem(updated));
        } catch (ConditionalCheckFailedException e) {
            // No item with that code - treat as "not found" like the in-memory impl.
            return Optional.empty();
        }
    }
}
