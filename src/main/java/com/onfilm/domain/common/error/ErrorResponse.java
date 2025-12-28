package com.onfilm.domain.common.error;


import java.util.List;

/**
 * @param code: 프론트가 분기하기 쉬운 에러 코드 (예: USER_NOT_FOUND, PERSON_NOT_FOUND, VALIDATION_FAILED)
 * @param message: 사람이 읽기 위한 설명 메시지
 * @param errors: 유효성 검증처럼 여러 필드에서 에러가 날 때 쓰는 상세 에러 목록
 */
public record ErrorResponse(String code, String message, List<FieldError> errors) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of());
    }

    public static ErrorResponse of(String code, String message, List<FieldError> errors) {
        return new ErrorResponse(code, message, errors);
    }

    public record FieldError(String field, String message) {}
}
