package com.guangge.Interview.practice.media;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 分片合并请求。#12 要求合并时校验完整性，checksumSha256 为整段媒体的 SHA-256（十六进制）。
 * 允许为空以兼容不校验的客户端，但前端总是带上。
 */
public record MediaMergeRequest(
        @Min(1) int totalChunks,
        @Min(1) long totalBytes,
        @NotBlank @Size(max = 255) String contentType,
        @Size(max = 64) String checksumSha256) {
}
