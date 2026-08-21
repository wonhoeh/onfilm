package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Person;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GalleryItemPrivacyRequest(
        @NotBlank(message = "갤러리 이미지 키는 필수입니다.")
        @Size(max = Person.STORAGE_KEY_MAX_LENGTH, message = "갤러리 이미지 키는 512자 이하여야 합니다.") String key,
        boolean isPrivate
) {
}
