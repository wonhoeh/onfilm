package com.onfilm.domain.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
}
