package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.CastType;
import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.MoviePersonRole;
import com.onfilm.domain.movie.entity.PersonRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MovieRoleRequest(
        @NotNull(message = "참여 역할은 필수입니다.")
        PersonRole role,
        CastType castType,
        @Size(
                max = MoviePersonRole.CHARACTER_NAME_MAX_LENGTH,
                message = "배역 이름은 100자 이하여야 합니다."
        )
        String characterName
) {
    @AssertTrue(message = "배우 역할은 캐스팅 구분이 필수이며 배우 정보는 배우 역할에만 사용할 수 있습니다.")
    public boolean isRoleDetailsValid() {
        if (role == null) {
            return true;
        }
        if (role == PersonRole.ACTOR) {
            return castType != null;
        }
        return castType == null && (characterName == null || characterName.isBlank());
    }

    public MoviePerson.RoleRegistration toRegistration() {
        return new MoviePerson.RoleRegistration(role, castType, characterName);
    }
}
