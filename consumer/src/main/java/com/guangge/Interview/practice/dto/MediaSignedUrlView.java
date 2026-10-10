package com.guangge.Interview.practice.dto;

import java.time.Instant;

public record MediaSignedUrlView(String url, Instant expiresAt, long ttlSeconds) { }
