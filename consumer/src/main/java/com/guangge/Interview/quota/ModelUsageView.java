package com.guangge.Interview.quota;

import java.time.Instant;

/**
 * 这个账号今天的模型额度。
 *
 * 前端拿它显示"今日 AI 额度还剩多少"，让用户在撞到上限之前就知道自己在花什么。
 */
public record ModelUsageView(
        boolean enabled,
        String date,
        int dailyLimit,
        long used,
        long remaining,
        Instant resetsAt,
        int concurrentLimit,
        long inFlight,
        String note) { }
