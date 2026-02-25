package com.sadia.production_grade_api_gateway.filter;

import com.sadia.production_grade_api_gateway.service.RedisRateLimiterService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-1)
public class RateLimitFilter implements GlobalFilter {
    private final RedisRateLimiterService rateLimiterService;
    public RateLimitFilter(RedisRateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = exchange.getRequest()
                .getRemoteAddress()
                .getAddress()
                .getHostAddress();

        return rateLimiterService.isAllowed(clientIp)
                .flatMap(allowed -> {
                    if (!allowed) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }

}
