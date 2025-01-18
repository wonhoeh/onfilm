package toyproject.onfilm.like.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import toyproject.onfilm.like.service.LikeService;

@RequiredArgsConstructor
@RequestMapping("/likes")
@RestController
public class LikeController {

    private final LikeService likeService;
    private static final String CLIENT_ID_COOKIE_NAME = "clientId";

    //좋아요 추가
    @PostMapping()
    public ResponseEntity<String> addLike(@RequestParam Long movieId, HttpServletRequest request) {
        String clientId = getClientIdFromCookies(request);
        boolean success = likeService.addLike(movieId, clientId);
        if (success) {
            return ResponseEntity.ok("좋아요 추가 성공");
        } else {
            return ResponseEntity.ok("좋아요 추가 실패: 이미 좋아요를 눌렀음");
        }
    }

    //좋아요 취소
    @DeleteMapping
    public ResponseEntity<String> removeLike(@RequestParam Long movieId, HttpServletRequest request) {
        String clientId = getClientIdFromCookies(request);
        boolean success = likeService.removeLike(movieId, clientId);
        if (success) {
            return ResponseEntity.ok("좋아요 삭제 성공");
        }
        else return ResponseEntity.ok("좋아요 삭제 실패: 좋아요를 찾을 수 없습니다");
    }

    //특정 영화의 좋아요 개수 조회
    @GetMapping("/{movieId}/count")
    public ResponseEntity<Long> getLikeCount(@PathVariable Long movieId) {
        long likeCount = likeService.getLikeCount(movieId);
        return ResponseEntity.ok(likeCount);
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
