package com.guangge.Interview.practice.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Builds the S3 client for either MinIO (endpoint override + path style + static keys)
 * or real S3 (default credential chain).
 */
@Configuration
@ConditionalOnProperty(name = "practice.media.provider", havingValue = "s3")
public class S3MediaStorageConfiguration {

    @Bean
    public S3Client s3Client(
            @Value("${practice.media.s3.endpoint:}") String endpoint,
            @Value("${practice.media.s3.region:us-east-1}") String region,
            @Value("${practice.media.s3.access-key:}") String accessKey,
            @Value("${practice.media.s3.secret-key:}") String secretKey,
            @Value("${practice.media.s3.path-style-access:true}") boolean pathStyleAccess) {

        var builder = S3Client.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build());

        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }
}
