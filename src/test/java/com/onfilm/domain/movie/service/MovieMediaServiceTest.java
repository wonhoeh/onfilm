package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.EmptyFileException;
import com.onfilm.domain.file.service.MediaEncodingService;
import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MovieMediaServiceTest {

    @Mock
    private MovieMediaTransactionService transactionService;

    @Mock
    private StorageService storageService;

    @Mock
    private StorageKeyFactory storageKeyFactory;

    @Mock
    private MediaEncodingService mediaEncodingService;

    private MovieMediaService service;

    @BeforeEach
    void setUp() {
        service = new MovieMediaService(
                transactionService,
                storageService,
                storageKeyFactory,
                mediaEncodingService
        );
    }

    @Test
    void synchronousUploadRejectsEmptyFileBeforeEncoding() {
        assertThatThrownBy(() -> service.replaceMovieFile(
                1L,
                new MockMultipartFile("file", new byte[0])
        )).isInstanceOfSatisfying(
                EmptyFileException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMPTY_FILE)
        );

        verify(transactionService).validateCanEdit(1L);
        verifyNoInteractions(mediaEncodingService, storageService);
    }

    @Test
    void compensatesStoredFileWhenDatabaseMutationFails() {
        String key = "movie/1/file/550e8400-e29b-41d4-a716-446655440000.mp4";
        MockMultipartFile file = new MockMultipartFile(
                "file", "movie.mp4", "video/mp4", new byte[]{1}
        );
        given(storageKeyFactory.movieFile(1L, ".mp4")).willReturn(key);
        given(mediaEncodingService.encodeVideo(any(Path.class), eq(720), eq(3000)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(storageService.toPublicUrl(key)).willReturn("https://cdn.example/" + key);
        doThrow(new IllegalStateException("database unavailable"))
                .when(transactionService)
                .replaceMovieFile(1L, key);

        assertThatThrownBy(() -> service.replaceMovieFile(1L, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(storageService).save(eq(key), any(Path.class));
        verify(storageService).delete(key);
    }
}
