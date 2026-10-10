package com.guangge.Interview.analysis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 本地兜底转写，用于没有语音服务凭证的开发与联调环境。
 *
 * 它仍然会去读一遍媒体资源，这样存储链路（本地磁盘 / 对象存储）也会被真实走一遍。
 * 启用方式：practice.asr.provider=stub
 */
@Component
@ConditionalOnProperty(name = "practice.asr.provider", havingValue = "stub")
public class StubAsrProvider implements AsrProvider {

    @Override
    public TranscriptResult transcribe(Resource media, String contentType) throws IOException {
        if (!media.exists()) {
            throw new IOException("Recorded media is missing");
        }
        return TranscriptResult.ofText(
                "（本地模拟转写）读取到 " + media.contentLength() + " 字节的录制内容，用于在没有语音服务时跑通流程。");
    }
}
