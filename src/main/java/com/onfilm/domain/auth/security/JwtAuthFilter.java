package com.onfilm.domain.auth.security;

import com.onfilm.domain.auth.config.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
/**
 * 요청이 들어올 때 access token을 읽어서, 유효하면 로그인된 사용자로 등록하는 필터입니다.
 * OncePerRequestFilter를 상속했기 때문에, HTTP 요청마다 한 번씩 실행됩니다.
 *   즉 브라우저가 API를 호출할 때마다 이 필터가 먼저 돌면서:
 *   - 토큰이 있는지 확인
 *   - 유효한지 검증
 *   - 인증 객체를 SecurityContext에 넣음
 * 한 줄 요약: 요청에서 access token을 헤더나 쿠키에서 꺼내고, 유효하면 Spring Security의 로그인 정보로 등록하는 코드
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;
    private final AuthProperties authProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/")
                || path.startsWith("/h2-console");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 1. Authorization 헤더 확인
        String auth = request.getHeader("Authorization");

        //System.out.println("[JWT] path=" + request.getRequestURI() + " auth=" + request.getHeader("Authorization"));

        // 2. 헤더 방식, 쿠키 방식 모두 지원하는 구조
        // 2-1. Bearer 토큰이면 그걸 우선 사용
        String token = null;
        if (auth != null && auth.startsWith("Bearer ")) {
            token = auth.substring(7);

        // 2-2. 헤더가 없으면 access cookie에서 찾음
        } else if (request.getCookies() != null) {
            // access token 쿠키 이름, 보통 access_token
            // 이름이 access_token인 쿠키를 찾고, 그 값(JWT)을 꺼냄
            String cookieName = authProperties.accessCookieNameOrDefault();
            for (var c : request.getCookies()) {
                if (cookieName.equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        // 3. 토큰 검증
        // 토큰이 존재하고 비어 있지 않고 검증을 통과한 경우
        // 검증 과정: 1. jwt 형식이 맞는지 / 2. 서명이 유효한지 / 3. 만료되지 않았는지
        if (token != null && !token.isBlank() && jwtProvider.validate(token)) {
            // 4. Authentication 객체 생성 후 등록
            // 토큰에서 userId를 꺼내서 Authentication 객체를 만듦
            // 그리고 그걸 SecurityContextHolder에 넣음
            // 위 과정을 통해 컨트롤러나 서비스에서 "이 요청은 로그인한 사용자 요청이다" 라고 인식할 수 있게 됨
            Authentication authentication = jwtProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 5. 다음 필터/컨트롤러로 넘김
        // 이 필터가 요청을 끝내는 게 아니라 인증 정보만 세팅하고 다음 단계로 넘기는 것
        filterChain.doFilter(request, response);
    }

}
