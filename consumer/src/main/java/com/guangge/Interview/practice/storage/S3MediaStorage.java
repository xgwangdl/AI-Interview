package com.guangge.Interview.practice.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.Optional;

/**
 * S3-compatible object storage (AWS S3, MinIO, ...). Selected with
 * {@code practice.media.provider=s3}; the client is built by
 * {@link S3MediaStorageConfiguration}.
 */
@Component
@ConditionalOnProperty(name = "practice.media.provider", havingValue = "s3")
public class S3MediaStorage implements MediaStorage {

    private final S3Client client;
    private final String bucket;

    public S3MediaStorage(S3Client client, @Value("${practice.media.s3.bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void store(String key, InputStream content, long contentLength) {
        client.putObject(builder -> builder.bucket(bucket).key(key),
                RequestBody.fromInputStream(content, contentLength));
    }

    @Override
    public Optional<Resource> find(String key) {
        try {
            client.headObject(builder -> builder.bucket(bucket).key(key));
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (NoSuchBucketException exception) {
            // 桶名配错是配置问题，不能伪装成「录像不存在」，否则排障会找错方向。
            throw exception;
        } catch (S3Exception exception) {
            // Only "missing" maps to absent; auth or network problems must surface.
            if (exception.statusCode() == 404) return Optional.empty();
            throw exception;
        }
        return Optional.of(new S3ObjectResource(client, bucket, key));
    }

    @Override
    public void delete(String key) {
        client.deleteObject(builder -> builder.bucket(bucket).key(key));
    }
}
