package com.guangge.Interview.practice.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs {@link S3MediaStorage} against a real S3-compatible server. Skipped unless
 * {@code -Ds3.integration=true} is passed, so ordinary builds stay hermetic.
 *
 * docker compose up -d s3mock && mvn -B test -Ds3.integration=true
 */
@EnabledIfSystemProperty(named = "s3.integration", matches = "true")
class S3MediaStorageIntegrationTest {

    private static final String ENDPOINT = System.getProperty("s3.endpoint", "http://localhost:9090");
    private static final String BUCKET = System.getProperty("s3.bucket", "light-interview-media");

    @Test
    void roundTripsRecordingBytesThroughAnS3CompatibleServer() throws IOException {
        S3MediaStorage storage = new S3MediaStorage(s3Client(), BUCKET);
        String key = "acceptance-" + UUID.randomUUID() + ".webm";
        byte[] payload = "recorded answer bytes".getBytes(StandardCharsets.UTF_8);

        storage.store(key, new ByteArrayInputStream(payload), payload.length);

        Resource stored = storage.find(key).orElseThrow();
        assertEquals(payload.length, stored.contentLength());
        assertArrayEquals(payload, stored.getInputStream().readAllBytes());

        storage.delete(key);
        assertTrue(storage.find(key).isEmpty());
    }

    @Test
    void reportsAbsentObjectsAsMissing() {
        S3MediaStorage storage = new S3MediaStorage(s3Client(), BUCKET);

        assertTrue(storage.find("missing-" + UUID.randomUUID() + ".webm").isEmpty());
    }

    private static S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(ENDPOINT))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
