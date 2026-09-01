package com.onfilm.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MySqlContainerEnvironmentIntegrationTest extends MySqlContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void integrationTestsUsePinnedMySqlAndApiDatabase() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        String currentUser = jdbcTemplate.queryForObject("SELECT CURRENT_USER()", String.class);
        String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        String characterSet = jdbcTemplate.queryForObject(
                "SELECT @@character_set_database",
                String.class
        );
        String collation = jdbcTemplate.queryForObject(
                "SELECT @@collation_database",
                String.class
        );
        String migrationVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history " +
                        "WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class
        );

        assertThat(database).isEqualTo("onfilm_api");
        assertThat(currentUser).startsWith("onfilm_api_app@");
        assertThat(version).startsWith("8.4.11");
        assertThat(characterSet).isEqualTo("utf8mb4");
        assertThat(collation).isEqualTo("utf8mb4_0900_ai_ci");
        assertThat(migrationVersion).isEqualTo("1");
    }
}
