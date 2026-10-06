package com.instaclone.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs "--&gt;"/"&lt;--" lines for every request (method, path, status, duration) with a short
 * request id in the MDC so every log line from a single request can be grepped together — never
 * logs headers or bodies, since request bodies can contain raw passwords (register/login).
 * Ordered ahead of Spring Security so rejected (401/403) requests are logged too, not just ones
 * that reach a controller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        MDC.put(REQUEST_ID_MDC_KEY, UUID.randomUUID().toString().substring(0, 8));
        String query = request.getQueryString();
        String path = query != null ? request.getRequestURI() + "?" + query : request.getRequestURI();
        long start = System.currentTimeMillis();
        log.info("--> {} {}", request.getMethod(), path);
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info(
                    "<-- {} {} {} ({} ms)",
                    request.getMethod(),
                    path,
                    response.getStatus(),
                    System.currentTimeMillis() - start);
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }
}
