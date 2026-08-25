package com.onfilm.domain.common.error;

import com.onfilm.domain.common.error.exception.DomainException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> new ErrorResponse.FieldError(err.getField(), err.getDefaultMessage()))
                .toList();

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ErrorResponse.of(
                        errorCode.name(),
                        errorCode.message(),
                        errors
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        return response(ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleRequestBindingFailure(Exception exception) {
        return response(ErrorCode.BAD_REQUEST);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ErrorResponse.of(errorCode.name(), errorCode.message()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return response(ErrorCode.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {
        return response(ErrorCode.DATA_INTEGRITY_VIOLATION);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        return response(ErrorCode.CONCURRENT_MEDIA_JOB_UPDATE);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedRuntimeException(RuntimeException exception) {
        log.error("Unhandled runtime exception", exception);
        return response(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ErrorResponse.of(errorCode.name(), errorCode.message()));
    }
}
