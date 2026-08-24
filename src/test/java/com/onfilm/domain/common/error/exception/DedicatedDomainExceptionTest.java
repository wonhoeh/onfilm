package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DedicatedDomainExceptionTest {

    @Test
    void 전용_예외는_도메인_예외이며_정해진_에러_코드를_가진다() {
        List<ExpectedException> exceptions = List.of(
                expected(new DuplicateEmailException(), ErrorCode.DUPLICATE_EMAIL),
                expected(new DuplicateUsernameException(), ErrorCode.DUPLICATE_USERNAME),
                expected(new InvalidProfileTagException("invalid tag"), ErrorCode.INVALID_PROFILE_TAG),
                expected(new InvalidRefreshTokenException(), ErrorCode.INVALID_REFRESH_TOKEN),
                expected(new MediaEncodeJobNotFoundException("job-id"), ErrorCode.MEDIA_ENCODE_JOB_NOT_FOUND),
                expected(new MediaUploadRequestNotFoundException("request-id"), ErrorCode.MEDIA_UPLOAD_REQUEST_NOT_FOUND),
                expected(new FilmographyItemNotFoundException(1L), ErrorCode.FILMOGRAPHY_ITEM_NOT_FOUND),
                expected(new MovieNotFoundException(1L), ErrorCode.MOVIE_NOT_FOUND),
                expected(new PersonNotFoundException(1L), ErrorCode.PERSON_NOT_FOUND),
                expected(new RefreshTokenReuseDetectedException(), ErrorCode.INVALID_REFRESH_TOKEN),
                expected(new StoryboardProjectNotFoundException(1L), ErrorCode.STORYBOARD_PROJECT_NOT_FOUND),
                expected(new StoryboardSceneNotFoundException(1L), ErrorCode.STORYBOARD_SCENE_NOT_FOUND),
                expected(new UserNotFoundException(1L), ErrorCode.USER_NOT_FOUND)
        );

        assertThat(exceptions).allSatisfy(expected -> {
            assertThat(expected.exception()).isInstanceOf(DomainException.class);
            assertThat(expected.exception().getErrorCode()).isEqualTo(expected.errorCode());
        });
    }

    @Test
    void 조회_예외는_식별자를_노출하지_않고_기본_메시지를_사용한다() {
        PersonNotFoundException exception = new PersonNotFoundException(123L);

        assertThat(exception.getMessage()).isEqualTo(ErrorCode.PERSON_NOT_FOUND.message());
        assertThat(exception.getMessage()).doesNotContain("123");
    }

    @Test
    void 프로필_태그_예외는_구체적인_검증_사유를_보존한다() {
        InvalidProfileTagException exception = new InvalidProfileTagException("tag is required");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PROFILE_TAG);
        assertThat(exception.getMessage()).isEqualTo("tag is required");
    }

    @Test
    void 리프레시_토큰_예외는_원인_예외를_보존한다() {
        RuntimeException cause = new RuntimeException("cause");

        InvalidRefreshTokenException exception = new InvalidRefreshTokenException(cause);

        assertThat(exception.getCause()).isSameAs(cause);
    }

    private static ExpectedException expected(DomainException exception, ErrorCode errorCode) {
        return new ExpectedException(exception, errorCode);
    }

    private record ExpectedException(DomainException exception, ErrorCode errorCode) {
    }
}
