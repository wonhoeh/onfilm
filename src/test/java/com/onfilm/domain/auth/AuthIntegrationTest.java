package com.onfilm.domain.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.auth.dto.LoginRequest;
import com.onfilm.domain.auth.dto.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signupThenLoginSucceeds() throws Exception {
        SignupRequest signup = new SignupRequest("user@example.com", "password123!");
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("user@example.com", "password123!");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void loginSetsRefreshCookieAndReturnsAccessToken() throws Exception {
        signup("cookie@example.com");

        var result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("cookie@example.com", "password123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refresh_token=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/auth");
    }

    @Test
    void refreshRotatesTokenAndOldTokenFails() throws Exception {
        signup("rotate@example.com");
        String refreshToken = loginAndExtractRefreshToken("rotate@example.com");

        var refreshResult = mockMvc.perform(post("/auth/refresh")
                        .header(HttpHeaders.COOKIE, "refresh_token=" + refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String newRefreshToken = extractCookieValue(refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/auth/refresh")
                        .header(HttpHeaders.COOKIE, "refresh_token=" + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsCookieAndRevokesToken() throws Exception {
        signup("logout@example.com");
        String refreshToken = loginAndExtractRefreshToken("logout@example.com");

        var logoutResult = mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.COOKIE, "refresh_token=" + refreshToken))
                .andExpect(status().isNoContent())
                .andReturn();

        String setCookie = logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("Max-Age=0");

        mockMvc.perform(post("/auth/refresh")
                        .header(HttpHeaders.COOKIE, "refresh_token=" + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRequiresAuthAndReturnsUser() throws Exception {
        signup("me@example.com");
        var loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("me@example.com", "password123!"))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"));

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private void signup(String email) throws Exception {
        SignupRequest signup = new SignupRequest(email, "password123!");
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

        return extractCookieValue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    private String extractCookieValue(String setCookie) {
        List<HttpCookie> cookies = HttpCookie.parse(setCookie);
        return cookies.get(0).getValue();
    }
}
