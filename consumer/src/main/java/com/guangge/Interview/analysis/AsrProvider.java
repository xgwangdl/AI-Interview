package com.guangge.Interview.analysis;

import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * Provider-agnostic speech recognition contract. Implementations must not be
 * referenced directly by business code so the vendor can be switched by configuration.
 */
public interface AsrProvider {
    TranscriptResult transcribe(Resource media, String contentType) throws IOException;
}
