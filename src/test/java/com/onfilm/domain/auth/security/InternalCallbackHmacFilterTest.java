package com.onfilm.domain.auth.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.SecurityErrorResponseWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InternalCallbackHmacFilterTest {
    private static final String SECRET = "test-media-encode-callback-secret-32-bytes";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesValidSignatureAndRejectsNonceReplay() throws Exception {
        InternalCallbackHmacFilter filter = filter();
        String nonce = UUID.randomUUID().toString();
        byte[] body = "{\"status\":\"PROCESSING\"}".getBytes(StandardCharsets.UTF_8);

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(request(nonce, body, NOW.getEpochSecond()), firstResponse, new MockFilterChain());
        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_MEDIA_WORKER");

        SecurityContextHolder.clearContext();
        MockHttpServletResponse replayResponse = new MockHttpServletResponse();
        filter.doFilter(request(nonce, body, NOW.getEpochSecond()), replayResponse, new MockFilterChain());
        assertErrorResponse(replayResponse, ErrorCode.INTERNAL_CALLBACK_AUTHENTICATION_FAILED);
    }

    @Test
    void rejectsExpiredTimestampAndTamperedBody() throws Exception {
        InternalCallbackHmacFilter filter = filter();
        MockHttpServletResponse expired = new MockHttpServletResponse();
        filter.doFilter(request(UUID.randomUUID().toString(), new byte[0],
                NOW.minus(Duration.ofMinutes(6)).getEpochSecond()), expired, new MockFilterChain());
        assertErrorResponse(expired, ErrorCode.INTERNAL_CALLBACK_AUTHENTICATION_FAILED);

        String nonce = UUID.randomUUID().toString();
        MockHttpServletRequest request = request(nonce, "original".getBytes(StandardCharsets.UTF_8), NOW.getEpochSecond());
        request.setContent("tampered".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse tampered = new MockHttpServletResponse();
        filter.doFilter(request, tampered, new MockFilterChain());
        assertErrorResponse(tampered, ErrorCode.INTERNAL_CALLBACK_AUTHENTICATION_FAILED);
    }

    @Test
    void rejectsRequestWithUnavailableCallbackConfiguration() throws Exception {
        InternalCallbackHmacFilter filter = filter("");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                new MockHttpServletRequest("POST", "/internal/api/media-jobs/job/complete"),
                response,
                new MockFilterChain()
        );

        assertErrorResponse(response, ErrorCode.INTERNAL_CALLBACK_UNAVAILABLE);
    }

    @Test
    void rejectsCallbackBodyLargerThanLimit() throws Exception {
        InternalCallbackHmacFilter filter = filter();
        byte[] oversizedBody = new byte[64 * 1024 + 1];
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request(UUID.randomUUID().toString(), oversizedBody, NOW.getEpochSecond()),
                response,
                new MockFilterChain()
        );

        assertErrorResponse(response, ErrorCode.PAYLOAD_TOO_LARGE);
    }

    private InternalCallbackHmacFilter filter() {
        return filter(SECRET);
    }

    private InternalCallbackHmacFilter filter(String secret) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("clock", Clock.fixed(NOW, ZoneOffset.UTC));
        return new InternalCallbackHmacFilter(
                secret,
                beans.getBeanProvider(Clock.class),
                new SecurityErrorResponseWriter(new ObjectMapper())
        );
    }

    private MockHttpServletRequest request(String nonce, byte[] body, long epochSeconds) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/api/media-jobs/job/complete");
        request.setContent(body);
        request.addHeader(InternalCallbackHmacFilter.TIMESTAMP_HEADER, Long.toString(epochSeconds));
        request.addHeader(InternalCallbackHmacFilter.NONCE_HEADER, nonce);
        request.addHeader(InternalCallbackHmacFilter.SIGNATURE_HEADER,
                sign(epochSeconds, nonce, body));
        return request;
    }

    private String sign(long epochSeconds, String nonce, byte[] body) throws Exception {
        String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        String canonical = epochSeconds + "\n" + nonce + "\nPOST\n"
                + "/internal/api/media-jobs/job/complete\n" + bodyHash;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertErrorResponse(
            MockHttpServletResponse response,
            ErrorCode errorCode
    ) throws Exception {
        JsonNode body = new ObjectMapper().readTree(response.getContentAsByteArray());

        assertThat(response.getStatus()).isEqualTo(errorCode.httpStatus().value());
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(body.get("code").asText()).isEqualTo(errorCode.name());
        assertThat(body.get("message").asText()).isEqualTo(errorCode.message());
        assertThat(body.get("errors").isArray()).isTrue();
        assertThat(body.get("errors").isEmpty()).isTrue();
    }
}
