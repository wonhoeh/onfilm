package com.onfilm.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public abstract class MySqlContainerSupport {

    private static final DockerImageName MYSQL_IMAGE =
            DockerImageName.parse("mysql:8.4.11");

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
            .withDatabaseName("onfilm_api")
            .withUsername("onfilm_api_app")
            .withPassword("onfilm_api_test_password")
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_ai_ci"
            )
            .withStartupTimeout(Duration.ofMinutes(3));

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");

        // Flyway V1 도입 전 기존 Hibernate 기반 통합 테스트를 MySQL로 옮기는 과도기 설정이다.
        // V1 적용 단계에서 validate로 교체한다.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
    }
}
