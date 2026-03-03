package com.sadia.production_grade_api_gateway.service;


import com.sadia.production_grade_api_gateway.configuration.RateLimiterProperties;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RedisRateLimiterService {
    private static final Logger log =
            LoggerFactory.getLogger(RedisRateLimiterService.class);
    private final ReactiveStringRedisTemplate redisTemplate;
     // Maximum requests allowed in window
//    private static final int LIMIT =5;
    // Window size in milliseconds (60 seconds)
//    private static final long WINDOW_SIZE_MS = 60000;

    private final RateLimiterProperties properties;

    public RedisRateLimiterService(ReactiveStringRedisTemplate redisTemplate, RateLimiterProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public Mono<Boolean> isAllowed(String key){

        long now = Instant.now().toEpochMilli();
        // Redis key for storing request timestamps per user/IP
        String redisKey = "rate_limit:" + key;
//        to handle concurrency, what if two request arrive at the same millisecond
        String member= String.valueOf(now) + "-" + UUID.randomUUID();
        // Step 1: Add current request timestamp to sorted set
        return redisTemplate.opsForZSet()
                .add(redisKey,member, now)
                // Step 2: Remove timestamps older than window
                .then(
                        redisTemplate.opsForZSet()
                                .removeRangeByScore(redisKey,   Range.closed(
                                        0.0,
                                        (double) (now - properties.getWindowSeconds() *1000L)
                                )
                )
                                // Step 3: Count how many requests remain in window
                .then(redisTemplate.opsForZSet().size(redisKey))
                                // Step 4: Apply rate limit decision
                                .flatMap(count -> {

                                    log.info("Current request count for key {} is {}", redisKey, count);

                                    if (count != null && count > properties.getLimit()) {
                                        log.warn("Rate limit exceeded for key {}", redisKey);
                                        return Mono.just(false);
                                    }

                                    // Align TTL with sliding window
                                    return redisTemplate.expire(
                                            redisKey,
                                            Duration.ofSeconds(properties.getWindowSeconds())
                                    ).thenReturn(true);
                                })

                                // 🔥 Fail-Open Strategy
                                .onErrorResume(ex -> {
                                    log.error(
                                            "Redis unavailable. Applying fail-open strategy. Allowing request.",
                                            ex
                                    );
                                    return Mono.just(true);
                                }));
        //NOTE- flatMap() is used when:
        //
        //Inside your transformation,
        //you are calling another async operation
        //that returns a Mono.

    }
}
