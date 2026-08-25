package com.onfilm.domain.movie.service;

import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PersonMediaServiceTest {

    @Mock
    private PersonMediaTransactionService transactionService;

    @Mock
    private StorageService storageService;

    @Mock
    private StorageKeyFactory storageKeyFactory;

    @Mock
    private MultipartFile file;

    private PersonMediaService service;

    @BeforeEach
    void setUp() {
        service = new PersonMediaService(
                transactionService,
                storageService,
                storageKeyFactory
        );
    }

    @Test
    void storesFileBeforeApplyingProfileImageInShortTransaction() {
        String key = "profile/1/550e8400-e29b-41d4-a716-446655440000.jpg";
        given(transactionService.getCurrentPersonId()).willReturn(1L);
        given(file.getOriginalFilename()).willReturn("profile.JPG");
        given(storageKeyFactory.profileAvatar(1L, ".jpg")).willReturn(key);
        given(storageService.toPublicUrl(key)).willReturn("https://cdn.example/" + key);

        UploadResultResponse response = service.replaceProfileImage(file);

        assertThat(response.key()).isEqualTo(key);
        assertThat(response.url()).isEqualTo("https://cdn.example/" + key);
        InOrder order = inOrder(storageService, transactionService);
        order.verify(storageService).save(key, file);
        order.verify(storageService).toPublicUrl(key);
        order.verify(transactionService).replaceProfileImage(key);
    }

    @Test
    void compensatesStoredFileWhenDatabaseMutationFails() {
        String key = "profile/1/550e8400-e29b-41d4-a716-446655440000.jpg";
        given(transactionService.getCurrentPersonId()).willReturn(1L);
        given(file.getOriginalFilename()).willReturn("profile.jpg");
        given(storageKeyFactory.profileAvatar(1L, ".jpg")).willReturn(key);
        given(storageService.toPublicUrl(key)).willReturn("https://cdn.example/" + key);
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(transactionService)
                .replaceProfileImage(key);

        assertThatThrownBy(() -> service.replaceProfileImage(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(storageService).delete(key);
    }

    @Test
    void doesNotMutatePersonWhenPublicUrlConversionFails() {
        String key = "profile/1/550e8400-e29b-41d4-a716-446655440000.jpg";
        given(transactionService.getCurrentPersonId()).willReturn(1L);
        given(file.getOriginalFilename()).willReturn("profile.jpg");
        given(storageKeyFactory.profileAvatar(1L, ".jpg")).willReturn(key);
        given(storageService.toPublicUrl(key))
                .willThrow(new IllegalStateException("public URL unavailable"));

        assertThatThrownBy(() -> service.replaceProfileImage(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("public URL unavailable");

        verify(transactionService, never()).replaceProfileImage(key);
        verify(storageService).delete(key);
    }

    @Test
    void deleteProfileImageDelegatesToTransactionService() {
        service.deleteProfileImage();

        verify(transactionService).deleteProfileImage();
    }
}
