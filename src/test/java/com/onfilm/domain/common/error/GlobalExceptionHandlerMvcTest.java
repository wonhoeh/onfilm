package com.onfilm.domain.common.error;

import com.onfilm.domain.common.error.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @MethodSource("domainExceptions")
    void springMvcMapsDomainExceptionUsingErrorCode(
            String path,
            ErrorCode errorCode
    ) throws Exception {
        mockMvc.perform(get("/test/errors/" + path))
                .andExpect(status().is(errorCode.httpStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.message()))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    private static Stream<Arguments> domainExceptions() {
        return Stream.of(
                Arguments.of("bad-request", ErrorCode.INVALID_STORAGE_KEY),
                Arguments.of("unauthorized", ErrorCode.INVALID_CREDENTIALS),
                Arguments.of("forbidden", ErrorCode.FORBIDDEN_MOVIE_ACCESS),
                Arguments.of("not-found", ErrorCode.PERSON_NOT_FOUND),
                Arguments.of("conflict", ErrorCode.DUPLICATE_EMAIL),
                Arguments.of("gone", ErrorCode.MEDIA_UPLOAD_REQUEST_EXPIRED),
                Arguments.of("unsupported-media", ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        );
    }

    @Test
    void malformedJsonUsesBadRequestErrorResponse() throws Exception {
        mockMvc.perform(post("/test/request/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.BAD_REQUEST.message()))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void missingRequestParameterUsesBadRequestErrorResponse() throws Exception {
        mockMvc.perform(get("/test/request/parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.BAD_REQUEST.message()))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void requestParameterTypeMismatchUsesBadRequestErrorResponse() throws Exception {
        mockMvc.perform(get("/test/request/parameter").param("id", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.BAD_REQUEST.message()))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @RestController
    private static class ExceptionController {

        @PostMapping("/test/request/body")
        void readBody(@RequestBody TestRequest request) {
        }

        @GetMapping("/test/request/parameter")
        void readParameter(@RequestParam Long id) {
        }

        @GetMapping("/test/errors/{type}")
        void throwDomainException(@PathVariable String type) {
            throw switch (type) {
                case "bad-request" -> new InvalidStorageKeyException();
                case "unauthorized" -> new InvalidCredentialsException();
                case "forbidden" -> new ForbiddenMovieAccessException();
                case "not-found" -> new PersonNotFoundException(1L);
                case "conflict" -> new DuplicateEmailException();
                case "gone" -> new MediaUploadRequestExpiredException();
                case "unsupported-media" -> new UnsupportedMediaTypeException();
                default -> new IllegalArgumentException("unsupported test type");
            };
        }
    }

    private record TestRequest(Long id) {
    }
}
