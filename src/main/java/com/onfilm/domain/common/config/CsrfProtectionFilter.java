package com.onfilm.domain.common.config;

import com.onfilm.domain.auth.config.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CsrfProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(),
            HttpMethod.HEAD.name(),
            HttpMethod.OPTIONS.name(),
            HttpMethod.TRACE.name()
    );

    private final AuthProperties authProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if (SAFE_METHODS.contains(method)) return true;
        String path = request.getRequestURI();
        return path.startsWith("/auth/login")
                || path.startsWith("/auth/signup")
                || path.startsWith("/auth/refresh")
                || path.startsWith("/auth/logout");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if (!isSameOrigin(origin, host) && !isSameOrigin(referer, host)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String csrfCookieName = authProperties.csrfCookieNameOrDefault();
        String csrfCookieValue = null;
        if (request.getCookies() != null) {
            for (var c : request.getCookies()) {
                if (csrfCookieName.equals(c.getName())) {
                    csrfCookieValue = c.getValue();
                    break;
                }
            }
        }
        String csrfHeader = request.getHeader("X-CSRF-TOKEN");
        if (csrfCookieValue == null || csrfCookieValue.isBlank() || csrfHeader == null || csrfHeader.isBlank()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (!csrfCookieValue.equals(csrfHeader)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSameOrigin(String originOrReferer, String host) {
        if (originOrReferer == null || originOrReferer.isBlank()) return false;
        try {
            java.net.URI uri = java.net.URI.create(originOrReferer);
            String h = uri.getHost();
            if (h == null) return false;
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            String hostWithPort = host.contains(":") ? host : host + port;
            return hostWithPort.equalsIgnoreCase(h + port) || host.equalsIgnoreCase(h);
        } catch (Exception e) {
            return false;
        }
    }
}
