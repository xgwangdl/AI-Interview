package com.guangge.Interview.practice.storage;

import org.springframework.core.io.AbstractResource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;

/**
 * Streams one S3 object. The stream is opened per call so the HTTP layer consumes
 * it lazily instead of buffering a whole recording in memory.
 */
final class S3ObjectResource extends AbstractResource {

    private final S3Client client;
    private final String bucket;
    private final String key;

    S3ObjectResource(S3Client client, String bucket, String key) {
        this.client = client;
        this.bucket = bucket;
        this.key = key;
    }

    @Override
    public String getDescription() {
        return "s3://" + bucket + "/" + key;
    }

    @Override
    public InputStream getInputStream() {
        return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public long contentLength() {
        return client.headObject(builder -> builder.bucket(bucket).key(key)).contentLength();
    }
}
