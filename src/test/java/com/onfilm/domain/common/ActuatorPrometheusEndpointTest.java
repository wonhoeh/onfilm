package com.onfilm.domain.common;

import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.metrics.MediaEncodeMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.prometheus.metrics.export.enabled=true",
        "management.metrics.distribution.percentiles-histogram.media.encode.job.duration=true"
})
@AutoConfigureMockMvc
class ActuatorPrometheusEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebEndpointProperties webEndpointProperties;

    @Autowired
    private MediaEncodeMetrics mediaEncodeMetrics;

    @Test
    void exposesOnlyRequiredActuatorEndpoints() {
        assertThat(webEndpointProperties.getExposure().getInclude())
                .containsExactlyInAnyOrder("health", "info", "prometheus");
    }

    @Test
    void exposesHealthWithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void exposesPrometheusMetricsWithoutAuthentication() throws Exception {
        mediaEncodeMetrics.recordJobTerminal(
                EncodeJobType.MOVIE,
                "success",
                Duration.ofSeconds(30)
        );

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("media_encode_outbox_records")))
                .andExpect(content().string(containsString("media_encode_job_records")))
                .andExpect(content().string(containsString(
                        "media_encode_job_duration_seconds_bucket")))
                .andExpect(content().string(containsString("application=\"onfilm-api\"")))
                .andExpect(content().string(containsString("environment=\"test\"")));
    }
}
