package com.sadia.production_grade_api_gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
public class RedisRateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterService.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    // Maximum requests allowed in window
    private static final int LIMIT = 5;

    // Window size in milliseconds (60 seconds)
    private static final long WINDOW_SIZE_MS = 60000;

    public RedisRateLimiterService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isAllowed(String key) {

        long now = Instant.now().toEpochMilli();
        String redisKey = "rate_limit:" + key;

        return redisTemplate.opsForZSet()

                // Step 1: Add current request timestamp to sorted set
                .add(redisKey, String.valueOf(now), now)

                // Step 2: Remove timestamps outside sliding window
                .then(redisTemplate.opsForZSet()
                        .removeRangeByScore(
                                redisKey,
                                Range.closed(
                                        0.0,
                                        (double) (now - WINDOW_SIZE_MS)
                                )
                        )
                )

                // Step 3: Count remaining timestamps inside window
                .then(redisTemplate.opsForZSet().size(redisKey))

                // Step 4: Decision logic
                .flatMap(count -> {

                    if (count != null && count > LIMIT) {
                        return Mono.just(false);
                    }

                    // Set TTL so Redis auto-cleans inactive keys
                    return redisTemplate
                            .expire(redisKey, Duration.ofMillis(WINDOW_SIZE_MS))
                            .thenReturn(true);
                })

                // 🔥 Fail-Open Strategy
                .onErrorResume(ex -> {

                    log.error(
                            "Redis unavailable. Applying fail-open strategy. Allowing request.",
                            ex
                    );

                    // Fail-open: allow request if Redis is unavailable
                    return Mono.just(true);
                });
    }
}