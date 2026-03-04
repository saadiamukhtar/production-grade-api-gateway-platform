package com.sadia.production_grade_api_gateway.filter;

import com.sadia.production_grade_api_gateway.service.RedisRateLimiterService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Order(-1)
public class RateLimitFilter implements GlobalFilter {
    private static final Logger log =
            LoggerFactory.getLogger(RateLimitFilter.class);
    private final RedisRateLimiterService rateLimiterService;
    private final MeterRegistry meterRegistry;
    private final Counter totalRequests;
    private final Counter rateLimitExceeded;
    public RateLimitFilter(RedisRateLimiterService rateLimiterService, MeterRegistry meterRegistry) {
        this.rateLimiterService = rateLimiterService;
        this.meterRegistry = meterRegistry;
        this.totalRequests = meterRegistry.counter("gateway.requests.total");
        this.rateLimitExceeded = meterRegistry.counter("gateway.rate_limit.exceeded");

    }
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        log.info("RateLimitFilter executing for path: {}",
                exchange.getRequest().getPath());

        totalRequests.increment();

        String clientIp = exchange.getRequest()
                .getRemoteAddress()
                .getAddress()
                .getHostAddress();

        return rateLimiterService.isAllowed(clientIp)

                .flatMap(allowed -> {

                    if (!allowed) {

                        rateLimitExceeded.increment();
                        log.warn("Rate limit exceeded for IP: {}", clientIp);

                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }

                    return chain.filter(exchange);
                })

                // 🔥 FAIL OPEN
                .onErrorResume(ex -> {

                    log.error("Rate limiter failure. Applying fail-open strategy.", ex);

                    // Allow request if rate limiter fails
                    return chain.filter(exchange);
                });
    }

}
