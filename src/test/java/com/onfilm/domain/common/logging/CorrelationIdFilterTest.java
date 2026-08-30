package com.onfilm.domain.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesValidRequestHeaderToMdcAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/people/1");
        request.addHeader(CorrelationIdContext.HEADER_NAME, "corr-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observed = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                observed.set(MDC.get(CorrelationIdContext.MDC_KEY)));

        assertThat(observed).hasValue("corr-123");
        assertThat(response.getHeader(CorrelationIdContext.HEADER_NAME)).isEqualTo("corr-123");
        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeRequestHeaderWithGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader(CorrelationIdContext.HEADER_NAME, "unsafe\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        String generated = response.getHeader(CorrelationIdContext.HEADER_NAME);
        assertThatCode(() -> UUID.fromString(generated)).doesNotThrowAnyException();
        assertThat(generated).isNotEqualTo("unsafe\nvalue");
    }
}
