package com.onfilm.domain.auth.dto;

import com.onfilm.domain.user.entity.RawPasswordPolicy;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Size(max = UserEmail.MAX_LENGTH)
        String email,
        @NotBlank @Size(min = RawPasswordPolicy.MIN_LENGTH)
        String password,

        @NotBlank
        @Size(min = Username.MIN_LENGTH, max = Username.MAX_LENGTH)
        String username
) {
}
