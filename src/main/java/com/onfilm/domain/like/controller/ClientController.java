package com.onfilm.domain.like.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RequestMapping("/client")
@RestController
public class ClientController {

    private static final String CLIENT_ID_COOKIE_NAME = "clientId";
    private static final int COOKIE_EXPIRATION = 60 * 60 * 24 * 365; //유효기간: 1년 (초단위)

    @GetMapping("/assign-id")
    public ResponseEntity<String> assignClientId(HttpServletRequest request, HttpServletResponse response) {
        //요청에서 쿠키를 확인
        String existingClientId = getClientIdCookieName(request);
        if (existingClientId != null ) {
            return ResponseEntity.ok("Client Id already exists: " + existingClientId);
        }

        //UUID 생성
        String clientId = UUID.randomUUID().toString();

        //쿠키에 저장
        Cookie cookie = new Cookie(CLIENT_ID_COOKIE_NAME, clientId);
        cookie.setHttpOnly(true);   //클라이언트에서 접근 불가
        cookie.setSecure(true);     //HTTPS에서만 전송
        cookie.setPath("/");        //모든 경로에서 사용 가능
        cookie.setMaxAge(COOKIE_EXPIRATION);    //쿠키 유효기간 설정
        response.addCookie(cookie);

        return ResponseEntity.ok("NEW Client ID assigned: " + clientId);
    }

    private String getClientIdCookieName(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if (CLIENT_ID_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
