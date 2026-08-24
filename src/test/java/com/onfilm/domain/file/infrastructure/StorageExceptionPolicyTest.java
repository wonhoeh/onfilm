package com.onfilm.domain.file.infrastructure;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.EmptyFileException;
import com.onfilm.domain.common.error.exception.InvalidStorageKeyException;
import com.onfilm.domain.file.infrastructure.local.LocalStorageService;
import com.onfilm.domain.file.infrastructure.s3.S3StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class StorageExceptionPolicyTest {

    @TempDir
    Path tempDirectory;

    @Test
    void localStorageRejectsTraversalAndEmptyFileWithDomainExceptions() {
        LocalStorageService storage = new LocalStorageService(
                tempDirectory.toString(),
                "/files",
                ""
        );

        assertInvalidStorageKey(() -> storage.exists("../outside.txt"));
        assertEmptyFile(() -> storage.save(
                "movie/1/file.mp4",
                new MockMultipartFile("file", new byte[0])
        ));
    }

    @Test
    void s3StorageUsesTheSameKeyAndEmptyFilePolicy() {
        S3Client s3Client = mock(S3Client.class);
        S3StorageService storage = new S3StorageService(
                s3Client,
                "bucket",
                "",
                "ap-northeast-2",
                ""
        );

        assertInvalidStorageKey(() -> storage.delete("../outside.txt"));
        assertEmptyFile(() -> storage.save(
                "movie/1/file.mp4",
                new MockMultipartFile("file", new byte[0])
        ));
        verifyNoInteractions(s3Client);
    }

    private static void assertInvalidStorageKey(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(InvalidStorageKeyException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STORAGE_KEY));
    }

    private static void assertEmptyFile(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(EmptyFileException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMPTY_FILE));
    }
}
