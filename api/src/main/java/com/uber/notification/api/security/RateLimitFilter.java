package com.uber.notification.api.security;

import com.uber.notification.application.port.CachePort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Fixed-window rate limiter for the notification hot paths
 * ({@code /api/v1/notifications/**}, {@code /api/v1/internal/**},
 * {@code /api/v1/admin/broadcast}).
 *
 * <p>Counts requests per principal (or IP for anonymous) in Redis-backed
 * {@link CachePort} windows of 60s. Fail-open: if the cache is unavailable the
 * request is allowed rather than failing a healthy API because Redis hiccuped.
 * Responds 429 + Retry-After when the budget is exhausted.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final CachePort cachePort;
    private final boolean enabled;
    private final int requestsPerMinute;

    public RateLimitFilter(@Autowired(required = false) CachePort cachePort,
                           @Value("${notification.rate-limit.enabled:true}") boolean enabled,
                           @Value("${notification.rate-limit.requests-per-minute:300}") int requestsPerMinute) {
        this.cachePort = cachePort;
        this.enabled = enabled;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!enabled || cachePort == null) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/notifications")
                && !path.startsWith("/api/v1/internal")
                && !path.startsWith("/api/v1/admin/broadcast");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String identity = identity(request);
        String window = String.valueOf(Instant.now().truncatedTo(ChronoUnit.MINUTES).getEpochSecond());
        String key = "ratelimit:" + request.getRequestURI() + ":" + identity + ":" + window;
        try {
            long count = cachePort.increment(key, 1, WINDOW);
            if (count > requestsPerMinute) {
                response.setStatus(429); // 429 Too Many Requests
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"errorCode\":\"RATE_LIMITED\",\"message\":\"Too many requests, slow down.\"}");
                return;
            }
        } catch (Exception e) {
            // Fail open: a cache outage must not take down the notification API.
            log.debug("Rate-limit check failed open", e);
        }
        chain.doFilter(request, response);
    }

    private String identity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtService.AuthenticatedPrincipal principal) {
            return "user:" + principal.userId();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
