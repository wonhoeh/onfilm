package com.onfilm.domain.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthPageBlockFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String method = request.getMethod();
        if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)) {
            String path = request.getRequestURI();
            if (path.startsWith("/auth")) {
                String accept = request.getHeader("Accept");
                if (accept != null && accept.contains("text/html")) {
                    response.sendRedirect("/404.html");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
