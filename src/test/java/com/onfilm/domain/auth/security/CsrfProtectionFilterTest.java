package com.onfilm.domain.auth.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.SecurityErrorResponseWriter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CsrfProtectionFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectedRequestUsesStandardForbiddenResponse() throws Exception {
        CsrfProtectionFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/people/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        ErrorCode errorCode = ErrorCode.CSRF_VALIDATION_FAILED;
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(errorCode.httpStatus().value());
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("code").asText()).isEqualTo(errorCode.name());
        assertThat(body.get("message").asText()).isEqualTo(errorCode.message());
        assertThat(body.get("errors").isArray()).isTrue();
        assertThat(body.get("errors").isEmpty()).isTrue();
    }

    @Test
    void validSameOriginAndDoubleSubmitTokenContinueFilterChain() throws Exception {
        CsrfProtectionFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/people/me");
        request.addHeader("Host", "example.com");
        request.addHeader("Origin", "https://example.com");
        request.addHeader("X-CSRF-TOKEN", "csrf-token");
        request.setCookies(new Cookie("XSRF-TOKEN", "csrf-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private CsrfProtectionFilter filter() {
        AuthProperties authProperties = mock(AuthProperties.class);
        given(authProperties.csrfCookieNameOrDefault()).willReturn("XSRF-TOKEN");
        return new CsrfProtectionFilter(
                authProperties,
                new SecurityErrorResponseWriter(objectMapper)
        );
    }
}
