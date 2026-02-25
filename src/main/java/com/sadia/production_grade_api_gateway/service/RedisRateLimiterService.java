package com.sadia.production_grade_api_gateway.service;


import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class RedisRateLimiterService {
    private final ReactiveStringRedisTemplate redisTemplate;
     // Maximum requests allowed in window
    private static final int LIMIT =5;
    // Window size in milliseconds (60 seconds)
    private static final long WINDOW_SIZE_MS = 60000;

    public RedisRateLimiterService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isAllowed(String key){

        long now = Instant.now().toEpochMilli();
        // Redis key for storing request timestamps per user/IP
        String redisKey = "rate_limit:" + key;
        // Step 1: Add current request timestamp to sorted set
        return redisTemplate.opsForZSet()
                .add(redisKey, String.valueOf(now), now)
                // Step 2: Remove timestamps older than window
                .then(
                        redisTemplate.opsForZSet()
                                .removeRangeByScore(redisKey,   Range.closed(
                                        0.0,
                                        (double) (now - WINDOW_SIZE_MS)
                                )
                )
                                // Step 3: Count how many requests remain in window
                .then(redisTemplate.opsForZSet().size(redisKey))
                                // Step 4: Decide whether to allow request
                .flatMap(count -> {
                    if (count != null && count > LIMIT) {
                        return Mono.just(false);
                    }
                        return redisTemplate.expire(redisKey, java.time.Duration.ofSeconds(60))
                                .thenReturn(true);

                }));
    }
}
