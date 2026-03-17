package com.onfilm.domain.file.infrastructure.local;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@Slf4j
@ConditionalOnProperty(name = "file.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileServeConfig implements WebMvcConfigurer {

    @Value("${file.storage.root:./local-storage}")
    private String rootDir;

    @Value("${file.storage.bucket:}")
    private String bucket;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String configuredBucket = (bucket == null) ? "" : bucket.trim();
        String location = configuredBucket.isBlank()
                ? Paths.get(rootDir).toAbsolutePath().toUri().toString()
                : Paths.get(rootDir).toAbsolutePath().resolve(configuredBucket).toUri().toString();
        log.info("[storage location] {}", location);
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location);
    }
}
