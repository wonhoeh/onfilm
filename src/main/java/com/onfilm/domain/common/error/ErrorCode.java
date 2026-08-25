package com.onfilm.domain.common.error;

import org.springframework.http.HttpStatus;

/**
 * API에 노출되는 오류 코드와 기본 응답 정책을 정의한다.
 *
 * <p>코드 이름은 클라이언트가 분기에 사용하는 안정적인 계약이므로,
 * 기존 코드의 의미를 변경하지 않고 새 코드를 추가하는 방식으로 관리한다.</p>
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "요청 값이 올바르지 않습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "이미 등록된 값이거나 데이터 제약조건을 위반했습니다."),
    CONCURRENT_MEDIA_JOB_UPDATE(HttpStatus.CONFLICT, "작업 상태가 동시에 변경되었습니다. 현재 상태를 다시 확인해 주세요."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "요청 본문 크기가 허용 범위를 초과했습니다."),
    INTERNAL_CALLBACK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "내부 콜백 서비스를 사용할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    PERSON_NOT_FOUND(HttpStatus.NOT_FOUND, "인물 정보를 찾을 수 없습니다."),
    MOVIE_NOT_FOUND(HttpStatus.NOT_FOUND, "영화를 찾을 수 없습니다."),
    STORYBOARD_PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "스토리보드 프로젝트를 찾을 수 없습니다."),
    STORYBOARD_SCENE_NOT_FOUND(HttpStatus.NOT_FOUND, "스토리보드 장면을 찾을 수 없습니다."),
    MEDIA_ENCODE_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "미디어 인코딩 작업을 찾을 수 없습니다."),
    MEDIA_UPLOAD_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "미디어 업로드 요청을 찾을 수 없습니다."),
    FILMOGRAPHY_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "필모그래피 항목을 찾을 수 없습니다."),

    INVALID_PROFILE_TAG(HttpStatus.BAD_REQUEST, "프로필 태그가 올바르지 않습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 사용자 이름입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INTERNAL_CALLBACK_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "내부 콜백 인증에 실패했습니다."),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CSRF_VALIDATION_FAILED(HttpStatus.FORBIDDEN, "CSRF 검증에 실패했습니다."),
    FORBIDDEN_PERSON_ACCESS(HttpStatus.FORBIDDEN, "해당 인물 정보에 접근할 권한이 없습니다."),
    FORBIDDEN_MOVIE_ACCESS(HttpStatus.FORBIDDEN, "해당 영화에 접근할 권한이 없습니다."),
    FORBIDDEN_MEDIA_UPLOAD_ACCESS(HttpStatus.FORBIDDEN, "해당 미디어 업로드 요청에 접근할 권한이 없습니다."),

    PERSON_NOT_LINKED(HttpStatus.CONFLICT, "사용자에게 인물 정보가 연결되어 있지 않습니다."),
    INVALID_MEDIA_JOB_STATUS_TRANSITION(HttpStatus.CONFLICT, "미디어 인코딩 작업의 상태를 변경할 수 없습니다."),
    MEDIA_UPLOAD_ALREADY_COMPLETED(HttpStatus.CONFLICT, "미디어 업로드 요청이 이미 다른 작업으로 완료되었습니다."),
    MEDIA_UPLOAD_REQUEST_EXPIRED(HttpStatus.GONE, "미디어 업로드 요청이 만료되었습니다."),

    INVALID_STORAGE_KEY(HttpStatus.BAD_REQUEST, "스토리지 키가 올바르지 않습니다."),
    STORAGE_KEY_NOT_OWNED(HttpStatus.FORBIDDEN, "해당 스토리지 키에 접근할 권한이 없습니다."),
    MEDIA_UPLOAD_REQUEST_MISMATCH(HttpStatus.BAD_REQUEST, "업로드 완료 정보가 발급된 요청과 일치하지 않습니다."),
    MEDIA_SOURCE_FILE_NOT_FOUND(HttpStatus.BAD_REQUEST, "업로드된 원본 파일을 찾을 수 없습니다."),
    MEDIA_OUTPUT_FILE_NOT_FOUND(HttpStatus.BAD_REQUEST, "인코딩 결과 파일을 찾을 수 없습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 미디어 형식입니다."),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}
