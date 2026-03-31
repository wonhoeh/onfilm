package com.onfilm.domain.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.auth.dto.LoginRequest;
import com.onfilm.domain.auth.dto.SignupRequest;
import com.onfilm.domain.token.repository.RefreshTokenRepository;
import com.onfilm.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.HttpCookie;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenConcurrencyTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void clean() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @DisplayName("동시에 같은 refresh token으로 rotate 요청 시 하나만 성공하고 하나는 401")
    @Test
    void 동시_rotate_요청_하나만_성공() throws Exception {
        // given
        signup("concurrent@example.com");
        String refreshToken = loginAndExtractRefreshToken("concurrent@example.com");

        int threadCount = 2;
        CountDownLatch ready = new CountDownLatch(threadCount); // 두 스레드가 준비될 때까지 대기
        CountDownLatch start = new CountDownLatch(1);           // 동시 출발 신호
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();  // 준비 완료 신호
                    start.await();      // 동시 출발 대기

                    mockMvc.perform(post("/auth/refresh")
                                    .cookie(new Cookie("refresh_token", refreshToken)))
                            .andDo(result -> {
                                if (result.getResponse().getStatus() == 200) {
                                    successCount.incrementAndGet();
                                } else {
                                    failCount.incrementAndGet();
                                }
                            });
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        ready.await(); // 두 스레드 모두 준비될 때까지 대기
        start.countDown(); // 동시 출발

        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
        // 유효한 refresh token은 정확히 1개만 존재해야 함
        assertThat(refreshTokenRepository.findAll().stream()
                .filter(t -> t.getRevokedAt() == null).count()).isEqualTo(1);
    }

    @DisplayName("이미 사용된 refresh token 재사용 시 해당 유저의 전체 토큰 삭제")
    @Test
    void 탈취된_토큰_재사용_시_전체_토큰_삭제() throws Exception {
        // given
        signup("reuse@example.com");
        String oldRefreshToken = loginAndExtractRefreshToken("reuse@example.com");

        // 정상 rotate - oldRefreshToken은 revoked, 새 토큰 발급
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRefreshToken)))
                .andExpect(status().isOk());

        // 토큰이 1개(유효) 존재하는지 확인
        assertThat(refreshTokenRepository.findAll().stream()
                .filter(t -> t.getRevokedAt() == null).count()).isEqualTo(1);

        // when - 이미 revoked된 토큰으로 재요청 (탈취 시나리오)
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRefreshToken)))
                .andExpect(status().isUnauthorized());

        // then - 해당 유저의 모든 토큰이 삭제되어야 함
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    private void signup(String email) throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(email, "password123!", "testuser"))))
                .andExpect(status().isCreated());
    }

    private String loginAndExtractRefreshToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "password123!"))))
                .andExpect(status().isOk())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        return extractCookieValue(setCookies, "refresh_token");
    }

    private String extractCookieValue(List<String> setCookies, String cookieName) {
        String setCookie = setCookies.stream()
                .filter(c -> c.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No " + cookieName + " cookie in response: " + setCookies));
        return HttpCookie.parse(setCookie).get(0).getValue();
    }
}
