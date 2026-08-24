package com.onfilm.domain.auth;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpCookie;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void clean() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DisplayName("회원가입 후 로그인 성공 및 access token 쿠키 발급 확인")
    @Test
    void signupThenLoginSucceeds() throws Exception {
        SignupRequest signup = new SignupRequest(
                "User@Example.COM",
                "password123!",
                "TestUser"
        );
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("USER@example.com", "password123!");
        var result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(extractCookieValue(
                result.getResponse().getHeaders(HttpHeaders.SET_COOKIE),
                "access_token"
        )).isNotBlank();
        assertThat(userRepository.findByEmail("user@example.com"))
                .get()
                .extracting(user -> user.getPerson())
                .isNotNull();
    }

    @Test
    void signupRejectsEmailAndUsernameDuplicatesWithConflictCodes() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(
                                "first@example.com",
                                "password123!",
                                "TestUser"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(
                                "FIRST@example.com",
                                "password123!",
                                "another-user"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(
                                "second@example.com",
                                "password123!",
                                "testuser"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
    }

    @Test
    void signupRejectsPasswordOverBcryptUtf8ByteLimit() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(
                                "user@example.com",
                                "가".repeat(25),
                                "testuser"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void loginFailureUsesStableUnauthorizedErrorCode() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(
                                "missing@example.com",
                                "password123!"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshWithoutCookieUsesInvalidRefreshTokenErrorCode() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }


    @DisplayName("로그인 시 access_token과 refresh_token 쿠키가 내려오는지 확인")
    @Test
    void loginSetsRefreshCookieAndReturnsAccessToken() throws Exception {
        signup("cookie@example.com");

        var result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("cookie@example.com", "password123!"))))
                .andExpect(status().isOk())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(extractCookieValue(setCookies, "access_token")).isNotBlank();
        assertThat(extractCookieValue(setCookies, "refresh_token")).isNotBlank();
        assertThat(setCookies).anyMatch(cookie ->
                cookie.startsWith("refresh_token=")
                        && cookie.contains("HttpOnly")
                        && cookie.contains("Path=/auth")
        );
    }

    @DisplayName("리프레시 토큰 회전(새 토큰 발급, 이전 토큰 무효) 확인")
    @Test
    void refreshRotatesTokenAndOldTokenFails() throws Exception {
        signup("rotate@example.com");
        String refreshToken = loginAndExtractRefreshToken("rotate@example.com");

        var refreshResult = mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(extractCookieValue(
                refreshResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE),
                "access_token"
        )).isNotBlank();

        String newRefreshToken = extractCookieValue(
                refreshResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE),
                "refresh_token"
        );
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("로그아웃 시 쿠키 삭제 및 리프레시 토큰 무효 확인")
    @Test
    void logoutClearsCookieAndRevokesToken() throws Exception {
        signup("logout@example.com");
        String refreshToken = loginAndExtractRefreshToken("logout@example.com");

        var logoutResult = mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        String setCookie = logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("Max-Age=0");

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("인증 필요 여부와 /auth/me 응답 검증")
    @Test
    void meRequiresAuthAndReturnsUser() throws Exception {
        signup("me@example.com");
        var loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("me@example.com", "password123!"))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractCookieValue(
                loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE),
                "access_token"
        );

        mockMvc.perform(get("/auth/me")
                        .cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"));

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private void signup(String email) throws Exception {
        SignupRequest signup = new SignupRequest(email, "password123!", "qwer");
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());
    }

    private String loginAndExtractRefreshToken(String email) throws Exception {
        var result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123!"))))
                .andExpect(status().isOk())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        return extractCookieValue(setCookies, "refresh_token");
    }

    private String extractCookieValue(List<String> setCookies, String cookieName) {
        String setCookie = setCookies.stream()
                .filter(c -> c.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + cookieName + " cookie in response: " + setCookies));

        return HttpCookie.parse(setCookie).get(0).getValue();
    }
}
