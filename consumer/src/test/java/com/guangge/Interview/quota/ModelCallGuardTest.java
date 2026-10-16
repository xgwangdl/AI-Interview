package com.guangge.Interview.quota;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 成本护栏。
 *
 * 这里测的是"上限真的挡得住"和"额度用完别把应用弄崩"两件事 ——
 * 它是给共享成本兜底的东西，一旦失效，坏消息是账单，好消息是没人会发现。
 */
class ModelCallGuardTest {

    @Test
    @DisplayName("每日额度用完就拒绝，并且不会把额度减成负数")
    void stopsAtTheDailyLimit() {
        ModelCallGuard guard = ModelCallGuard.inMemory(true, 2, 5);
        AtomicInteger calls = new AtomicInteger();

        assertThat(guard.call("smoke", "面试出题", calls::incrementAndGet)).isEqualTo(1);
        assertThat(guard.call("smoke", "面试出题", calls::incrementAndGet)).isEqualTo(2);
        assertThatThrownBy(() -> guard.call("smoke", "面试出题", calls::incrementAndGet))
                .isInstanceOf(ModelQuotaExceededException.class)
                .hasMessageContaining("额度");

        assertThat(calls.get()).isEqualTo(2);
        ModelUsageView usage = guard.usage("smoke");
        assertThat(usage.used()).isEqualTo(2);
        assertThat(usage.remaining()).isZero();
        // 被拒绝的那次不该留在计数里，否则"剩余额度"会一直显示 0 而看不出真实用量
        assertThat(usage.used()).isEqualTo(usage.dailyLimit());
    }

    @Test
    @DisplayName("并发上限：同时开着的请求数超了就拒绝，跑完立刻释放")
    void limitsConcurrencyAndReleasesIt() {
        ModelCallGuard guard = ModelCallGuard.inMemory(true, 100, 1);

        try (ModelLease first = guard.tryAcquire("smoke", "追问")) {
            assertThat(first.allowed()).isTrue();
            ModelLease second = guard.tryAcquire("smoke", "追问");
            assertThat(second.allowed()).isFalse();
            assertThat(second.reason()).contains("同时");
            second.close();
        }

        try (ModelLease third = guard.tryAcquire("smoke", "追问")) {
            assertThat(third.allowed()).isTrue();
        }
        assertThat(guard.usage("smoke").inFlight()).isZero();
    }

    @Test
    @DisplayName("账号之间互不影响：别人烧完了不该挡住我")
    void countsPerAccount() {
        ModelCallGuard guard = ModelCallGuard.inMemory(true, 1, 5);

        guard.call("smoke", "面试出题", () -> "ok");
        assertThatThrownBy(() -> guard.call("smoke", "面试出题", () -> "ok"))
                .isInstanceOf(ModelQuotaExceededException.class);
        assertThat(guard.call("guangge", "面试出题", () -> "ok")).isEqualTo("ok");
        assertThat(guard.usage("guangge").remaining()).isZero();
    }

    @Test
    @DisplayName("关掉护栏就完全不拦（本地开发与压测要能跑通）")
    void canBeSwitchedOff() {
        ModelCallGuard guard = ModelCallGuard.inMemory(false, 1, 1);

        for (int index = 0; index < 5; index++) {
            assertThat(guard.call("smoke", "面试出题", () -> "ok")).isEqualTo("ok");
        }
        ModelUsageView usage = guard.usage("smoke");
        assertThat(usage.enabled()).isFalse();
        assertThat(usage.used()).isZero();
    }

    @Test
    @DisplayName("调用失败也照样扣额度：失败往往同样烧了 token")
    void countsFailedCalls() {
        ModelCallGuard guard = ModelCallGuard.inMemory(true, 3, 3);

        assertThatThrownBy(() -> guard.call("smoke", "简历优化", () -> {
            throw new IllegalStateException("上游 500");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(guard.usage("smoke").used()).isEqualTo(1);
        assertThat(guard.usage("smoke").inFlight()).isZero();
    }
}
