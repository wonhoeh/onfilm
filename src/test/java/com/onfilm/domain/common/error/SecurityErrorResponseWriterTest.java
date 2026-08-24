package com.onfilm.domain.common.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorResponseWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecurityErrorResponseWriter writer = new SecurityErrorResponseWriter(objectMapper);

    @Test
    void writesTheSameErrorResponseContractOutsideControllerAdvice() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, ErrorCode.ACCESS_DENIED);

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(body.get("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body.get("message").asText()).isEqualTo(ErrorCode.ACCESS_DENIED.message());
        assertThat(body.get("errors").isArray()).isTrue();
        assertThat(body.get("errors").isEmpty()).isTrue();
    }
}
