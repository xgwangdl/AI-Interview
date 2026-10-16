package com.guangge.Interview.quota;

import java.time.Instant;

/**
 * 一次模型调用的许可。
 *
 * {@code close()} 只归还"并发名额"（在飞的这次请求结束了）；
 * **每日额度不返还** —— 调用一旦发出去，钱就已经花掉了，退回来只是自欺欺人。
 */
public record ModelLease(
        boolean allowed,
        long used,
        int dailyLimit,
        int concurrentLimit,
        long inFlight,
        Instant resetsAt,
        String reason,
        Runnable onClose) implements AutoCloseable {

    @Override
    public void close() {
        if (onClose != null) onClose.run();
    }

    public long remaining() {
        return Math.max(0, dailyLimit - used);
    }

    /** 额度护栏关闭时用：不计数、不拒绝，但对调用方保持同一套接口。 */
    static ModelLease unlimited(int dailyLimit, int concurrentLimit) {
        return new ModelLease(true, 0, dailyLimit, concurrentLimit, 0, null, null, null);
    }
}
