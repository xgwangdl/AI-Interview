package com.guangge.Interview.practice.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3MediaStorageTest {

    private final S3Client client = mock(S3Client.class);
    private final S3MediaStorage storage = new S3MediaStorage(client, "light-interview-media");

    @Test
    void reportsAMissingObjectAsAbsent() {
        when(client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
                .thenThrow(NoSuchKeyException.builder().message("missing key").build());

        assertTrue(storage.find("answer.webm").isEmpty());
    }

    @Test
    void doesNotReportAMissingBucketAsAbsentObject() {
        when(client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
                .thenThrow(NoSuchBucketException.builder().message("missing bucket").build());

        assertThrows(NoSuchBucketException.class, () -> storage.find("answer.webm"));
    }
}
