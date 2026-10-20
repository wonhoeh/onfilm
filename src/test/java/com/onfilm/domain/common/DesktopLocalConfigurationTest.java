package com.onfilm.domain.common;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DesktopLocalConfigurationTest {
    @Test
    void localProfileKeepsPersistentSchemaAndLoopbackBinding() throws Exception {
        var properties = new YamlPropertySourceLoader()
                .load("local", new ClassPathResource("application-local.yml")).get(0);
        assertThat(properties.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(properties.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo(false);
        assertThat(properties.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(properties.getProperty("file.storage.root")).isEqualTo("${LOCAL_STORAGE_ROOT}");
        assertThat(properties.getProperty("spring.profiles.include")).isNull();
    }

    @Test
    void readinessIdentifiesTheLaunchedInstanceWithoutExposingSecrets() throws Exception {
        MockMvcBuilders.standaloneSetup(new DesktopHealthController("test-instance")).build()
                .perform(get("/health/desktop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("onfilm-api"))
                .andExpect(jsonPath("$.instanceId").value("test-instance"));
    }

    @Test
    void desktopEndpointOnlyExistsInLocalProfile() {
        var runner = new ApplicationContextRunner().withUserConfiguration(DesktopHealthController.class)
                .withPropertyValues("onfilm.desktop.instance-id=test-instance");
        runner.withPropertyValues("spring.profiles.active=local").run(context ->
                assertThat(context).hasSingleBean(DesktopHealthController.class));
        runner.withPropertyValues("spring.profiles.active=prod").run(context ->
                assertThat(context).doesNotHaveBean(DesktopHealthController.class));
        runner.withPropertyValues("spring.profiles.active=dev").run(context ->
                assertThat(context).doesNotHaveBean(DesktopHealthController.class));
    }
}
