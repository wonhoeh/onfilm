package com.onfilm.domain.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = CorrelationIdContext.resolve(
                request.getHeader(CorrelationIdContext.HEADER_NAME)
        );
        long startedAt = System.nanoTime();
        response.setHeader(CorrelationIdContext.HEADER_NAME, correlationId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable(
                CorrelationIdContext.MDC_KEY,
                correlationId
        )) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                log.info("HTTP request completed. {} {} {} {} {}",
                        kv("eventType", "HTTP_REQUEST_COMPLETED"),
                        kv("method", request.getMethod()),
                        kv("path", request.getRequestURI()),
                        kv("status", response.getStatus()),
                        kv("elapsedMs", elapsedMs));
            }
        }
    }
}
