package com.guangge.Interview.practice.media;

import java.util.List;
import java.util.UUID;

/**
 * 分片上传的进度视图。客户端刷新页面后靠它知道哪些分片已经在服务端，
 * 只补传缺失的部分，而不是整段重传。
 */
public record MediaUploadStatusView(
        UUID answerId,
        int totalChunks,
        List<Integer> receivedChunks,
        boolean readyToMerge) {
}
