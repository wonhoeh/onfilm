package com.onfilm.domain.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void 오류_코드는_HTTP_상태와_기본_메시지를_제공한다() {
        ErrorCode errorCode = ErrorCode.PERSON_NOT_FOUND;

        assertThat(errorCode.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errorCode.message()).isEqualTo("인물 정보를 찾을 수 없습니다.");
    }

    @Test
    void 모든_오류_코드는_HTTP_상태와_비어있지_않은_메시지를_가진다() {
        assertThat(ErrorCode.values()).allSatisfy(errorCode -> {
            assertThat(errorCode.httpStatus()).isNotNull();
            assertThat(errorCode.message()).isNotBlank();
        });
    }

    @Test
    void 공통_오류도_ErrorCode에서_HTTP_정책을_제공한다() {
        assertThat(Map.of(
                ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                ErrorCode.DATA_INTEGRITY_VIOLATION, HttpStatus.CONFLICT,
                ErrorCode.CONCURRENT_MEDIA_JOB_UPDATE, HttpStatus.CONFLICT,
                ErrorCode.PAYLOAD_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE,
                ErrorCode.INTERNAL_CALLBACK_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR
        )).allSatisfy((errorCode, httpStatus) ->
                assertThat(errorCode.httpStatus()).isEqualTo(httpStatus)
        );
    }
}
