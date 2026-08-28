package com.urlshortener.config;

import com.urlshortener.model.ShortUrl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

/**
 * Wires the DynamoDB clients. Active only under the "dynamodb" profile so the
 * default (in-memory) runtime never touches the AWS SDK.
 *
 * <p>Two clients are exposed on purpose:
 * <ul>
 *   <li>{@link DynamoDbEnhancedClient} for object-mapped put/get of {@link ShortUrl};</li>
 *   <li>the low-level {@link DynamoDbClient} for the atomic counter update expression,
 *       which the enhanced client cannot express directly.</li>
 * </ul>
 */
@Configuration
@Profile("dynamodb")
@EnableConfigurationProperties(AwsDynamoProperties.class)
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(AwsDynamoProperties props) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(props.getRegion()))
                .credentialsProvider(credentialsProvider(props));

        if (props.hasEndpointOverride()) {
            builder.endpointOverride(URI.create(props.getEndpoint()));
        }
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public DynamoDbTable<ShortUrl> shortUrlTable(DynamoDbEnhancedClient enhancedClient,
                                                AwsDynamoProperties props) {
        return enhancedClient.table(props.getTableName(), TableSchema.fromBean(ShortUrl.class));
    }

    private static AwsCredentialsProvider credentialsProvider(AwsDynamoProperties props) {
        if (props.hasStaticCredentials()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey()));
        }
        // Real environments: env vars, shared profile, or the EKS pod's IAM role (IRSA).
        return DefaultCredentialsProvider.create();
    }
}
