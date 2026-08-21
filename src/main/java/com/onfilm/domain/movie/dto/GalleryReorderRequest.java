package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Person;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GalleryReorderRequest(
        @NotNull(message = "갤러리 이미지 순서는 필수입니다.")
        List<@NotBlank(message = "갤러리 이미지 키는 공백일 수 없습니다.")
                @Size(max = Person.STORAGE_KEY_MAX_LENGTH, message = "갤러리 이미지 키는 512자 이하여야 합니다.") String> keys
) {
}
