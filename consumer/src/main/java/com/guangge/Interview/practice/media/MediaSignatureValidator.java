package com.guangge.Interview.practice.media;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 在上传阶段挡住明显无效的媒体。
 *
 * 这里只做容器签名和最小长度检查，不在请求线程里解码整段视频：
 * 17 字节占位文件、文本文件和未知容器应立即失败；损坏的合法容器仍由后台分析给出最终结论。
 */
public final class MediaSignatureValidator {

    /** 正常录制的视频远大于该值；阈值只用于拦截明显占位/截断内容。 */
    public static final long MIN_VIDEO_BYTES = 256;
    private static final int HEADER_BYTES = 32;
    private static final byte[] EBML = new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3};
    private static final byte[] OGG = ascii("OggS");
    private static final byte[] FLV = ascii("FLV");

    private MediaSignatureValidator() {
    }

    /**
     * 校验后返回可继续读取的流。调用方负责关闭底层输入流。
     */
    public static InputStream requireSupportedVideo(InputStream content, long size) throws IOException {
        if (content == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "录制文件为空");
        }
        if (size < MIN_VIDEO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "录制文件过小或不完整，请重新录制后再上传");
        }

        InputStream source = content instanceof BufferedInputStream buffered
                ? buffered
                : new BufferedInputStream(content, 8192);
        source.mark(HEADER_BYTES + 1);
        byte[] header = source.readNBytes(HEADER_BYTES);
        source.reset();

        if (header.length < 12 || !isSupportedContainer(header)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "无法识别录制文件格式，请重新录制后上传 WebM 或 MP4 视频");
        }
        return source;
    }

    static boolean isSupportedContainer(byte[] header) {
        if (header == null || header.length < 12) return false;
        if (startsWith(header, 0, EBML)) return true;
        if (startsWith(header, 0, OGG) || startsWith(header, 0, FLV)) return true;
        if (asciiEquals(header, 0, "RIFF") && asciiEquals(header, 8, "AVI ")) return true;
        // ISO Base Media File Format: MP4 / MOV 通常在第 4 字节开始出现 ftyp box。
        for (int offset = 0; offset <= Math.min(8, header.length - 4); offset++) {
            if (asciiEquals(header, offset, "ftyp")) return true;
        }
        return false;
    }

    private static boolean startsWith(byte[] source, int offset, byte[] prefix) {
        if (offset + prefix.length > source.length) return false;
        return Arrays.equals(Arrays.copyOfRange(source, offset, offset + prefix.length), prefix);
    }

    private static boolean asciiEquals(byte[] source, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (offset + bytes.length > source.length) return false;
        for (int index = 0; index < bytes.length; index++) {
            if (source[offset + index] != bytes[index]) return false;
        }
        return true;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
