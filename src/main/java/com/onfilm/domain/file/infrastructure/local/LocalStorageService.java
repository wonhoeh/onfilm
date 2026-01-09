package com.onfilm.domain.file.infrastructure.local;

import com.onfilm.domain.file.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Profile("dev")
@Service
public class LocalStorageService implements StorageService {

    private final Path rootPath;
    private final String publicBaseUrl;

    public LocalStorageService(
            @Value("${file.storage.root}") String rootDir,
            @Value("${file.public-base-url}") String publicBaseUrl
    ) {
        this.rootPath = Paths.get(rootDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public String save(String key, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("EMPTY_FILE");

        Path target = rootPath.resolve(key).normalize();
        if (!target.startsWith(rootPath)) throw new SecurityException("INVALID_PATH");

        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        Path target = rootPath.resolve(key).normalize();
        if (!target.startsWith(rootPath)) throw new SecurityException("INVALID_PATH");
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toPublicUrl(String key) {
        if (key == null || key.isBlank()) return null;

        String normalized = key.replace("\\", "/");
        while (normalized.startsWith("/")) normalized = normalized.substring(1);

        String base = publicBaseUrl;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        return base + "/" + normalized;
    }
}
