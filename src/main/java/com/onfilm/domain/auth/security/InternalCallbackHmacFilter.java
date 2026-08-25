package com.onfilm.domain.auth.security;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.SecurityErrorResponseWriter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InternalCallbackHmacFilter extends OncePerRequestFilter {
    public static final String TIMESTAMP_HEADER = "X-Onfilm-Timestamp";
    public static final String NONCE_HEADER = "X-Onfilm-Nonce";
    public static final String SIGNATURE_HEADER = "X-Onfilm-Signature";
    private static final Duration ALLOWED_SKEW = Duration.ofMinutes(5);
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final byte[] secret;
    private final Clock clock;
    private final SecurityErrorResponseWriter errorResponseWriter;
    private final Map<String, Instant> usedNonces = new ConcurrentHashMap<>();

    public InternalCallbackHmacFilter(
            @Value("${media-encode.callback-secret:}") String secret,
            ObjectProvider<Clock> clockProvider,
            SecurityErrorResponseWriter errorResponseWriter
    ) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (secret.length < 32) {
            errorResponseWriter.write(response, ErrorCode.INTERNAL_CALLBACK_UNAVAILABLE);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            errorResponseWriter.write(response, ErrorCode.PAYLOAD_TOO_LARGE);
            return;
        }
        if (!authenticate(request, body)) {
            errorResponseWriter.write(
                    response,
                    ErrorCode.INTERNAL_CALLBACK_AUTHENTICATION_FAILED
            );
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "media-worker", null, List.of(new SimpleGrantedAuthority("ROLE_MEDIA_WORKER"))));
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private boolean authenticate(HttpServletRequest request, byte[] body) {
        try {
            Instant now = clock.instant();
            long epochSeconds = Long.parseLong(request.getHeader(TIMESTAMP_HEADER));
            Instant timestamp = Instant.ofEpochSecond(epochSeconds);
            if (Duration.between(timestamp, now).abs().compareTo(ALLOWED_SKEW) > 0) return false;

            String nonce = request.getHeader(NONCE_HEADER);
            UUID.fromString(nonce);
            evictExpiredNonces(now);
            if (usedNonces.putIfAbsent(nonce, now.plus(ALLOWED_SKEW)) != null) return false;

            String signature = request.getHeader(SIGNATURE_HEADER);
            String canonical = epochSeconds + "\n" + nonce + "\n"
                    + request.getMethod() + "\n" + request.getRequestURI() + "\n" + sha256(body);
            byte[] expected = hmac(canonical);
            byte[] actual = HexFormat.of().parseHex(signature);
            if (!MessageDigest.isEqual(expected, actual)) {
                usedNonces.remove(nonce);
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void evictExpiredNonces(Instant now) {
        usedNonces.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC initialization failed", exception);
        }
    }

    private String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 initialization failed", exception);
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) { }
                @Override public int read() { return input.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
