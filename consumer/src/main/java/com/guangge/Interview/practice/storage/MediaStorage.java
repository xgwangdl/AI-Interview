package com.guangge.Interview.practice.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface MediaStorage {

    void store(String key, InputStream content, long contentLength) throws IOException;

    /** Loads stored media for playback; empty when the object does not exist. */
    Optional<Resource> find(String key);

    void delete(String key) throws IOException;
}
