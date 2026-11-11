package com.webox.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Correlates every request/response and emits one structured access log line. */
@Component
@Order(1)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        RequestContext.init(requestId);
        response.setHeader("X-Request-ID", requestId);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            JsonLog.info("http_request",
                    "method", request.getMethod(),
                    "path", request.getRequestURI(),
                    "status", response.getStatus(),
                    "durationMs", System.currentTimeMillis() - start);
            RequestContext.clear();
        }
    }
}
