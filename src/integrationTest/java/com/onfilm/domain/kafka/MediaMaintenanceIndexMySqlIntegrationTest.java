package com.onfilm.domain.kafka;

import com.onfilm.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MediaMaintenanceIndexMySqlIntegrationTest extends MySqlContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesMediaMaintenanceIndexesWithExpectedColumnOrder() {
        assertIndexColumns(
                "media_encode_jobs",
                "idx_media_encode_job_status_completed",
                "status", "completed_at"
        );
        assertIndexColumns(
                "media_encode_outbox",
                "idx_media_outbox_status_published",
                "status", "published_at"
        );
        assertIndexColumns(
                "media_encode_outbox",
                "idx_media_outbox_status_created",
                "status", "created_at"
        );
        assertIndexColumns(
                "media_encode_outbox",
                "idx_media_outbox_dispatch",
                "status", "next_attempt_at"
        );
    }

    private void assertIndexColumns(String tableName, String indexName, String... expectedColumns) {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                ORDER BY seq_in_index
                """, String.class, tableName, indexName);

        assertThat(columns).containsExactly(expectedColumns);
    }
}
