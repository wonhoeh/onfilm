package com.onfilm.domain.kafka.service;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class MediaEncodeJobQueryServiceTest {

    @Test
    void statusQueryDoesNotExposeJobOwnedByAnotherUser() {
        MediaEncodeJobRepository repository = mock(MediaEncodeJobRepository.class);
        MediaEncodeJobQueryService service = new MediaEncodeJobQueryService(repository);
        given(repository.findByIdAndRequestedByUserId("job-id", 1L))
                .willReturn(Optional.empty());

        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::currentUserId).thenReturn(1L);

            assertThatThrownBy(() -> service.getJobStatus("job-id"))
                    .isInstanceOfSatisfying(MediaEncodeJobNotFoundException.class, exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.MEDIA_ENCODE_JOB_NOT_FOUND));
        }

        verify(repository).findByIdAndRequestedByUserId("job-id", 1L);
    }
}
