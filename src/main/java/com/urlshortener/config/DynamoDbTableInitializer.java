package com.urlshortener.config;

import com.urlshortener.model.ShortUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

/**
 * Creates the DynamoDB table on startup when
 * {@code aws.dynamodb.create-table-on-startup=true}.
 *
 * <p>Meant for local development against LocalStack / DynamoDB Local. In real
 * environments the table is provisioned by infrastructure-as-code and this flag
 * stays {@code false}.
 */
@Component
@Profile("dynamodb")
public class DynamoDbTableInitializer {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbTableInitializer.class);

    private final DynamoDbTable<ShortUrl> table;
    private final DynamoDbClient client;
    private final AwsDynamoProperties props;

    public DynamoDbTableInitializer(DynamoDbTable<ShortUrl> table,
                                   DynamoDbClient client,
                                   AwsDynamoProperties props) {
        this.table = table;
        this.client = client;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createTableIfRequested() {
        if (!props.isCreateTableOnStartup()) {
            return;
        }
        try {
            table.createTable();
            client.waiter().waitUntilTableExists(b -> b.tableName(props.getTableName()));
            log.info("Created DynamoDB table '{}'", props.getTableName());
        } catch (ResourceInUseException e) {
            log.info("DynamoDB table '{}' already exists", props.getTableName());
        } catch (DynamoDbException e) {
            log.warn("Could not create DynamoDB table '{}': {}", props.getTableName(), e.getMessage());
        }
    }
}
