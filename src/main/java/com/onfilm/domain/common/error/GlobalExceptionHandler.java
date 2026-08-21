package com.onfilm.domain.common.error;

import com.onfilm.domain.common.error.exception.DuplicateEmailException;
import com.onfilm.domain.common.error.exception.DuplicateUsernameException;
import com.onfilm.domain.common.error.exception.InvalidProfileTagException;
import com.onfilm.domain.common.error.exception.InvalidRefreshTokenException;
import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.error.exception.MovieNotFoundException;
import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardProjectNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardSceneNotFoundException;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.common.error.exception.RefreshTokenReuseDetectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> new ErrorResponse.FieldError(err.getField(), err.getDefaultMessage()))
                .toList();

        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "VALIDATION_FAILED",
                        "요청 값이 올바르지 않습니다.",
                        errors
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("VALIDATION_FAILED", "요청 값이 올바르지 않습니다."));
    }

    @ExceptionHandler(PersonNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePersonNotFound(PersonNotFoundException e) {
        return ResponseEntity.status(NOT_FOUND)
                .body(ErrorResponse.of("PERSON_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(NOT_FOUND)
                .body(ErrorResponse.of("USER_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(MovieNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMovieNotFound(MovieNotFoundException e) {
        return ResponseEntity.status(NOT_FOUND)
                .body(ErrorResponse.of("MOVIE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(StoryboardSceneNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStoryboardSceneNotFound(StoryboardSceneNotFoundException e) {
        return ResponseEntity.status(NOT_FOUND)
                .body(ErrorResponse.of("STORYBOARD_SCENE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(StoryboardProjectNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStoryboardProjectNotFound(StoryboardProjectNotFoundException e) {
        return ResponseEntity.status(NOT_FOUND)
                .body(ErrorResponse.of("STORYBOARD_PROJECT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(InvalidProfileTagException.class)
    public ResponseEntity<ErrorResponse> handleInvalidProfileTag(InvalidProfileTagException e) {
        return ResponseEntity.status(BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_PROFILE_TAG", e.getMessage()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DUPLICATE_EMAIL", e.getMessage()));
    }

    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUsername(DuplicateUsernameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DUPLICATE_USERNAME", e.getMessage()));
    }

    @ExceptionHandler({
            InvalidRefreshTokenException.class,
            RefreshTokenReuseDetectedException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(
                        "INVALID_REFRESH_TOKEN",
                        "Invalid refresh token"
                ));
    }

    @ExceptionHandler(MediaEncodeJobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMediaEncodeJobNotFound(MediaEncodeJobNotFoundException e) {
        return ResponseEntity.status(NOT_FOUND)
                .body(ErrorResponse.of("MEDIA_ENCODE_JOB_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(BAD_REQUEST)
                .body(ErrorResponse.of("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "DATA_INTEGRITY_VIOLATION",
                        "이미 등록된 값이거나 데이터 제약조건을 위반했습니다."
                ));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "CONCURRENT_MEDIA_JOB_UPDATE",
                        "작업 상태가 동시에 변경되었습니다. 현재 상태를 다시 확인해 주세요."
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        HttpStatus status = switch (e.getMessage()) {
            case "FORBIDDEN_MOVIE_ACCESS", "FORBIDDEN_MEDIA_JOB_ACCESS",
                 "FORBIDDEN_MEDIA_UPLOAD_ACCESS" -> HttpStatus.FORBIDDEN;
            case "INVALID_MEDIA_JOB_STATUS_TRANSITION", "MEDIA_UPLOAD_ALREADY_COMPLETED" -> HttpStatus.CONFLICT;
            case "MEDIA_UPLOAD_REQUEST_EXPIRED" -> HttpStatus.GONE;
            default -> BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(e.getMessage(), e.getMessage()));
    }
}
