package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for the {@code aws.dynamodb.*} settings in application.yml.
 */
@ConfigurationProperties(prefix = "aws.dynamodb")
public class AwsDynamoProperties {

    /** DynamoDB table name. */
    private String tableName = "url-shortener";

    /** AWS region the table lives in. */
    private String region = "us-east-1";

    /** Optional endpoint override (LocalStack / DynamoDB Local). Blank = real AWS. */
    private String endpoint = "";

    /** Static access key for local runs. Blank = DefaultCredentialsProvider. */
    private String accessKey = "";

    /** Static secret key for local runs. Blank = DefaultCredentialsProvider. */
    private String secretKey = "";

    /** Create the table on startup if missing (local/dev convenience). */
    private boolean createTableOnStartup = false;

    public boolean hasEndpointOverride() {
        return endpoint != null && !endpoint.isBlank();
    }

    public boolean hasStaticCredentials() {
        return accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isCreateTableOnStartup() {
        return createTableOnStartup;
    }

    public void setCreateTableOnStartup(boolean createTableOnStartup) {
        this.createTableOnStartup = createTableOnStartup;
    }
}
