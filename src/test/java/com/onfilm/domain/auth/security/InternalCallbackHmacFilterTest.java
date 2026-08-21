package com.onfilm.domain.auth.security;

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
        assertThat(replayResponse.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsExpiredTimestampAndTamperedBody() throws Exception {
        InternalCallbackHmacFilter filter = filter();
        MockHttpServletResponse expired = new MockHttpServletResponse();
        filter.doFilter(request(UUID.randomUUID().toString(), new byte[0],
                NOW.minus(Duration.ofMinutes(6)).getEpochSecond()), expired, new MockFilterChain());
        assertThat(expired.getStatus()).isEqualTo(401);

        String nonce = UUID.randomUUID().toString();
        MockHttpServletRequest request = request(nonce, "original".getBytes(StandardCharsets.UTF_8), NOW.getEpochSecond());
        request.setContent("tampered".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse tampered = new MockHttpServletResponse();
        filter.doFilter(request, tampered, new MockFilterChain());
        assertThat(tampered.getStatus()).isEqualTo(401);
    }

    private InternalCallbackHmacFilter filter() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("clock", Clock.fixed(NOW, ZoneOffset.UTC));
        return new InternalCallbackHmacFilter(SECRET, beans.getBeanProvider(Clock.class));
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
}
