package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.PersonRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

public record CreateMovieRequest(
        @NotBlank(message = "영화 제목은 필수입니다.") String title,
        @Min(value = Movie.RUNTIME_MIN, message = "상영 시간은 1분 이상이어야 합니다.")
        @Max(value = Movie.RUNTIME_MAX, message = "상영 시간은 1000분 이하여야 합니다.") int runtime,
        @NotNull(message = "개봉 연도는 필수입니다.")
        @Min(value = Movie.RELEASE_YEAR_MIN, message = "개봉 연도는 1900년 이상이어야 합니다.") Integer releaseYear,
        @NotBlank(message = "영화 스토리지 키는 필수입니다.")
        @Size(max = Movie.STORAGE_KEY_MAX_LENGTH, message = "영화 스토리지 키는 512자 이하여야 합니다.") String movieUrl,
        @Size(max = Movie.STORAGE_KEY_MAX_LENGTH, message = "썸네일 스토리지 키는 512자 이하여야 합니다.") String thumbnailUrl,
        @NotNull(message = "장르 목록은 필수입니다.")
        List<@NotNull(message = "장르는 null일 수 없습니다.") @Valid MovieGenreRequest> genres,
        @NotNull(message = "관람 등급은 필수입니다.") AgeRating ageRating,
        @NotEmpty(message = "참여 역할은 하나 이상 필요합니다.")
        @Size(max = 3, message = "참여 역할은 최대 3개까지 등록할 수 있습니다.")
        List<@NotNull(message = "참여 역할은 null일 수 없습니다.") @Valid MovieRoleRequest> roles
) {
    @AssertTrue(message = "동일한 참여 역할은 중복해서 등록할 수 없습니다.")
    public boolean isRolesUnique() {
        if (roles == null) {
            return true;
        }
        List<PersonRole> roleTypes = roles.stream()
                .filter(role -> role != null && role.role() != null)
                .map(MovieRoleRequest::role)
                .toList();
        return new HashSet<>(roleTypes).size() == roleTypes.size();
    }
}
