package com.guangge.Interview.practice.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface MediaStorage {
    void store(String key, InputStream content) throws IOException;
    Path resolve(String key);
    void delete(String key) throws IOException;
}
