package com.onfilm.domain.common.error;

import com.onfilm.domain.common.error.exception.InvalidProfileTagException;
import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.error.exception.MovieNotFoundException;
import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardProjectNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardSceneNotFoundException;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        HttpStatus status = switch (e.getMessage()) {
            case "FORBIDDEN_MOVIE_ACCESS", "FORBIDDEN_MEDIA_JOB_ACCESS" -> HttpStatus.FORBIDDEN;
            case "MEDIA_ENCODE_PRODUCER_NOT_CONFIGURED", "PRESIGNED_UPLOAD_NOT_CONFIGURED" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "INVALID_MEDIA_JOB_STATUS_TRANSITION" -> BAD_REQUEST;
            default -> BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(e.getMessage(), e.getMessage()));
    }
}
