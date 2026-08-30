package com.onfilm.domain.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringConfigurationTest {
    private static final Path MONITORING = Path.of("infra", "monitoring");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pinsMonitoringImagesAndKeepsPortsLocal() throws Exception {
        String compose = Files.readString(MONITORING.resolve("docker-compose.yml"));

        assertThat(compose)
                .contains("prom/prometheus:v3.14.0")
                .contains("grafana/grafana:13.1.3")
                .contains("127.0.0.1:${PROMETHEUS_PORT:-9090}:9090")
                .contains("127.0.0.1:${GRAFANA_PORT:-3000}:3000")
                .contains("host.docker.internal:host-gateway");
        assertThat(Files.exists(MONITORING.resolve(".env.example"))).isTrue();
    }

    @Test
    void configuresApiAndWorkerPrometheusTargets() throws Exception {
        String prometheus = Files.readString(
                MONITORING.resolve("prometheus/prometheus.yml")
        );
        String compose = Files.readString(MONITORING.resolve("docker-compose.yml"));

        assertThat(prometheus)
                .contains("/etc/prometheus/rules/*.yml")
                .contains("job_name: onfilm-api")
                .contains("host.docker.internal:8080")
                .contains("job_name: onfilm-encoding-worker")
                .contains("host.docker.internal:8082")
                .contains("metrics_path: /actuator/prometheus");
        assertThat(compose).contains("./prometheus/rules:/etc/prometheus/rules:ro");
    }

    @Test
    void definesInitialOperationalAlertsWithoutHighCardinalityLabels() throws Exception {
        String alerts = Files.readString(MONITORING.resolve(
                "prometheus/rules/onfilm-alerts.yml"
        ));

        assertThat(alerts)
                .contains("alert: OnfilmApiDown")
                .contains("alert: OnfilmEncodingWorkerDown")
                .contains("alert: MediaOutboxDeadRecordsDetected")
                .contains("alert: MediaOutboxPendingTooOld")
                .contains("alert: MediaWorkerDltReceived")
                .contains("alert: MediaKafkaConsumerLagSustained")
                .contains("alert: MediaEncodingFailureRateHigh")
                .contains("alert: MediaEncodingTimeoutDetected")
                .contains("alert: MediaCallbackFailurePendingTooOld")
                .contains("alert: OnfilmApiHighServerErrorRate")
                .contains("severity: critical")
                .contains("severity: warning");
        assertThat(alerts)
                .doesNotContain("jobId:")
                .doesNotContain("movieId:")
                .doesNotContain("userId:")
                .doesNotContain("requestId:")
                .doesNotContain("correlationId:");
    }

    @Test
    void provisionsDashboardWithKnownDatasourceAndLowCardinalityQueries() throws Exception {
        String datasource = Files.readString(MONITORING.resolve(
                "grafana/provisioning/datasources/prometheus.yml"
        ));
        JsonNode dashboard = objectMapper.readTree(Files.readString(MONITORING.resolve(
                "grafana/dashboards/onfilm-media-operations.json"
        )));
        List<String> expressions = dashboard.findValues("expr").stream()
                .map(JsonNode::asText)
                .toList();

        assertThat(datasource)
                .contains("uid: onfilm-prometheus")
                .contains("url: http://prometheus:9090");
        assertThat(dashboard.path("uid").asText()).isEqualTo("onfilm-media-operations");
        assertThat(dashboard.path("panels").size()).isGreaterThanOrEqualTo(17);
        assertThat(expressions)
                .anyMatch(query -> query.contains("ALERTS{alertstate=\"firing\"}"))
                .anyMatch(query -> query.contains("media_encode_job_records"))
                .anyMatch(query -> query.contains("media_encode_outbox_records"))
                .anyMatch(query -> query.contains("media_encode_worker_stage_duration_seconds_bucket"))
                .anyMatch(query -> query.contains("media_encode_worker_inbox_records"));
        assertThat(expressions)
                .noneMatch(query -> query.contains("jobId")
                        || query.contains("movieId")
                        || query.contains("userId")
                        || query.contains("requestId")
                        || query.contains("correlationId"));
    }
}
