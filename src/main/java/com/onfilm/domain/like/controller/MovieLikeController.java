package com.onfilm.domain.like.controller;

import com.onfilm.domain.like.service.MovieLikeService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/movie-likes")
@RestController
public class MovieLikeController {

    private final MovieLikeService movieLikeService;
    private static final String CLIENT_ID_COOKIE_NAME = "clientId";

    //영화의 좋아요 조회
    @GetMapping("/{id}")
    public ResponseEntity<Long> getLikeCount(@PathVariable Long id) {
        long likeCount = movieLikeService.getLikeCount(id);
        return ResponseEntity.ok(likeCount);
    }

    //좋아요 추가
    @PostMapping("/{id}")
    public ResponseEntity<String> addLike(@PathVariable Long id, HttpServletRequest request) {
        String clientId = getClientIdFromCookies(request);
        boolean success = movieLikeService.addLike(id, clientId);
        if (success) {
            return ResponseEntity.ok("좋아요 추가 성공");
        } else {
            return ResponseEntity.ok("좋아요 추가 실패: 이미 좋아요를 눌렀음");
        }
    }

    //좋아요 취소
    @DeleteMapping("/{id}")
    public ResponseEntity<String> removeLike(@PathVariable Long id, HttpServletRequest request) {
        String clientId = getClientIdFromCookies(request);
        boolean success = movieLikeService.removeLike(id, clientId);
        if (success) {
            return ResponseEntity.ok("좋아요 삭제 성공");
        }
        else return ResponseEntity.ok("좋아요 삭제 실패: 좋아요를 찾을 수 없습니다");
    }



    private String getClientIdFromCookies(HttpServletRequest request) {
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
