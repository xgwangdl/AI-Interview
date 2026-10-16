package com.guangge.Interview.quota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 模型调用的成本护栏：每个账号**每日调用上限** + **并发上限**。
 *
 * 之前系统对模型调用没有任何计量：一个账号写个脚本连点"生成题目"就能把额度烧光，
 * 而这对所有其他用户是共享的成本。登录限流只挡住了"猜密码"，挡不住这个。
 *
 * 三条设计取舍：
 * 1. 计数放 Redis（多实例共享、重启不丢）；**Redis 不可用时退到进程内存**并记一条警告 ——
 *    护栏失效不该让整个应用不可用，退化的方向是"照常花钱"而不是"谁都别想用"。
 * 2. 只有**用户触发的、会花模型钱的调用**计数（出题 / 追问 / 判分 / 简历 / 语音合成 / 分析）。
 *    本地沙箱判题、视频上传、报告查询都不算。
 * 3. 额度是扣减式的：先占名额再调用。调用失败也扣，因为失败往往同样消耗了 token。
 */
@Component
public class ModelCallGuard {

    private static final Logger log = LoggerFactory.getLogger(ModelCallGuard.class);
    /** 在飞计数的兜底 TTL：进程被 kill 时不会把账号永久锁在"并发已满"。 */
    private static final Duration IN_FLIGHT_TTL = Duration.ofMinutes(10);

    private final CounterStore redis;
    private final CounterStore memory = new InMemoryCounterStore();
    private final boolean enabled;
    private final int dailyLimit;
    private final int concurrentLimit;
    private final ZoneId zone;
    /** Redis 挂过一次就改用内存计数，免得每个请求都白等一次连接超时。 */
    private volatile boolean redisUnavailable;

    /**
     * 注意 @Autowired：这个类有两个构造器，Spring 不会自己挑，
     * 不标注的话它去找无参构造器，启动直接失败（踩过）。
     */
    @Autowired
    public ModelCallGuard(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${quota.model.enabled:true}") boolean enabled,
            @Value("${quota.model.daily-limit:120}") int dailyLimit,
            @Value("${quota.model.max-concurrent:3}") int concurrentLimit,
            @Value("${quota.model.zone:Asia/Shanghai}") String zone) {
        this(redisTemplate == null ? null : new RedisCounterStore(redisTemplate),
                enabled, dailyLimit, concurrentLimit, zone);
    }

    /** 不接 Redis 的实例：计数只在本进程内存里（测试，或单机部署里没有 Redis 时）。 */
    public static ModelCallGuard inMemory(boolean enabled, int dailyLimit, int concurrentLimit) {
        return new ModelCallGuard((CounterStore) null, enabled, dailyLimit, concurrentLimit, "Asia/Shanghai");
    }

    private ModelCallGuard(CounterStore redis, boolean enabled, int dailyLimit, int concurrentLimit, String zone) {
        this.redis = redis;
        this.enabled = enabled;
        this.dailyLimit = Math.max(1, dailyLimit);
        this.concurrentLimit = Math.max(1, concurrentLimit);
        this.zone = ZoneId.of(zone);
    }

    /**
     * 只占额度、拿不到就抛 429（并发名额由调用方用 try-with-resources 释放）。
     *
     * 给"事务方法内部"用：像简历优化那样的方法会把业务异常吞成一条失败记录，
     * 额度不足必须在进入业务逻辑之前就拒绝，否则用户拿到的是一句含糊的"优化失败"。
     */
    public ModelLease require(String ownerId, String kind) {
        ModelLease lease = tryAcquire(ownerId, kind);
        if (!lease.allowed()) {
            log.info("模型额度已满，拒绝调用：owner={} kind={} reason={}", ownerId, kind, lease.reason());
            throw new ModelQuotaExceededException(lease.reason());
        }
        return lease;
    }

    /**
     * 同步接口用：拿不到额度直接抛 429。
     *
     * 并发名额在这里占、在 {@code work} 跑完后释放（无论成败）。
     */
    public <T> T call(String ownerId, String kind, Supplier<T> work) {
        try (ModelLease lease = require(ownerId, kind)) {
            return work.get();
        }
    }

    /**
     * 拿一次额度，拿不到也不抛异常。
     *
     * 异步链路（分析任务）用它：额度用完时应当把任务推迟到明天，
     * 而不是把用户的报告打成"分析失败"。
     */
    public ModelLease tryAcquire(String ownerId, String kind) {
        if (!enabled) return ModelLease.unlimited(dailyLimit, concurrentLimit);
        String owner = ownerId == null || ownerId.isBlank() ? "anonymous" : ownerId;
        Instant resetsAt = resetsAt();

        String liveKey = "quota:model-live:" + owner;
        long inFlight = increment(liveKey, IN_FLIGHT_TTL);
        if (inFlight > concurrentLimit) {
            decrement(liveKey);
            return new ModelLease(false, 0, dailyLimit, concurrentLimit, inFlight - 1, resetsAt,
                    "同时进行的 AI 请求太多了（上限 " + concurrentLimit + " 个），等前一个跑完再试", null);
        }
        Runnable release = () -> decrement(liveKey);

        String dailyKey = dailyKey(owner);
        long used = increment(dailyKey, Duration.between(Instant.now(), resetsAt).plusSeconds(60));
        if (used > dailyLimit) {
            // 超出的这一次要还回去：否则"今日已用"会一直往上飘，页面上显示的剩余额度就是错的
            decrement(dailyKey);
            release.run();
            return new ModelLease(false, dailyLimit, dailyLimit, concurrentLimit, inFlight - 1, resetsAt,
                    "今天的 AI 额度已经用完了（每天 " + dailyLimit + " 次，明天 0 点重置）", null);
        }
        return new ModelLease(true, used, dailyLimit, concurrentLimit, inFlight, resetsAt, null, release);
    }

    public ModelUsageView usage(String ownerId) {
        String owner = ownerId == null || ownerId.isBlank() ? "anonymous" : ownerId;
        long used = read(dailyKey(owner));
        long inFlight = read("quota:model-live:" + owner);
        return new ModelUsageView(
                enabled,
                LocalDate.now(zone).toString(),
                dailyLimit,
                used,
                Math.max(0, dailyLimit - used),
                resetsAt(),
                concurrentLimit,
                inFlight,
                "出题、追问、评分判分、简历优化与语音合成都会计入；每天为你保留 " + dailyLimit + " 次。");
    }

    /** 下次额度重置的时间：本地时区的下一个 0 点。 */
    private Instant resetsAt() {
        return ZonedDateTime.now(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
    }

    private String dailyKey(String owner) {
        return "quota:model:" + owner + ":" + LocalDate.now(zone);
    }

    private long increment(String key, Duration ttl) {
        if (redis != null && !redisUnavailable) {
            try {
                return redis.increment(key, ttl);
            } catch (RuntimeException exception) {
                degrade(exception);
                return memory.increment(key, ttl);
            }
        }
        return memory.increment(key, ttl);
    }

    private void decrement(String key) {
        if (redis != null && !redisUnavailable) {
            try {
                redis.decrement(key);
                return;
            } catch (RuntimeException exception) {
                degrade(exception);
                memory.decrement(key);
                return;
            }
        }
        memory.decrement(key);
    }

    private long read(String key) {
        if (redis != null && !redisUnavailable) {
            try {
                return redis.get(key);
            } catch (RuntimeException exception) {
                degrade(exception);
                return memory.get(key);
            }
        }
        return memory.get(key);
    }

    /** 只警告一次：真的连不上时，每个请求都刷一条日志反而会把有用的日志淹掉。 */
    private void degrade(RuntimeException exception) {
        if (redisUnavailable) return;
        redisUnavailable = true;
        log.warn("Redis 不可用，模型额度改为按进程内存统计（重启即清零）：{}", exception.getMessage());
    }

    /** 计数的最小接口：Redis 与内存两种实现都只是"自增 / 自减 / 读"。 */
    interface CounterStore {
        long increment(String key, Duration ttl);

        long decrement(String key);

        long get(String key);
    }

    /**
     * Redis 计数。
     *
     * 每次自增都重设 TTL：日额度键的 TTL 是"到今晚 0 点"，在飞键的 TTL 是兜底值，
     * 重设不会把窗口推长，反而能在 Redis 重启后把丢失的 TTL 补回来。
     */
    private static final class RedisCounterStore implements CounterStore {
        private final RedisTemplate<String, Object> template;

        private RedisCounterStore(RedisTemplate<String, Object> template) {
            this.template = template;
        }

        @Override
        public long increment(String key, Duration ttl) {
            Long value = template.opsForValue().increment(key);
            if (value == null) throw new IllegalStateException("Redis INCR 没有返回结果");
            template.expire(key, ttl);
            return value;
        }

        @Override
        public long decrement(String key) {
            Long value = template.opsForValue().decrement(key);
            return value == null ? 0 : value;
        }

        @Override
        public long get(String key) {
            Object raw = template.opsForValue().get(key);
            if (raw == null) return 0;
            try {
                return Long.parseLong(raw.toString().trim());
            } catch (NumberFormatException notANumber) {
                // 键被别的用途写过（或者手工改过），当作没计数，别把用户挡在门外
                return 0;
            }
        }
    }

    /** 进程内兜底：单实例有效，多实例各算各的 —— 这是退化方案，不是等价方案。 */
    private static final class InMemoryCounterStore implements CounterStore {
        private final Map<String, Entry> counters = new ConcurrentHashMap<>();

        @Override
        public long increment(String key, Duration ttl) {
            Entry entry = counters.compute(key, (ignored, current) ->
                    current == null || current.expired() ? new Entry(new AtomicLong(), Instant.now().plus(ttl))
                            : current);
            return entry.value().incrementAndGet();
        }

        @Override
        public long decrement(String key) {
            Entry entry = counters.get(key);
            if (entry == null || entry.expired()) return 0;
            return entry.value().decrementAndGet();
        }

        @Override
        public long get(String key) {
            Entry entry = counters.get(key);
            if (entry == null || entry.expired()) return 0;
            return Math.max(0, entry.value().get());
        }

        private record Entry(AtomicLong value, Instant expiresAt) {
            boolean expired() {
                return Instant.now().isAfter(expiresAt);
            }
        }
    }
}
