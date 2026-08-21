package com.onfilm.domain.auth.dto;

import com.onfilm.domain.auth.validation.ValidRawPassword;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Email
        @Size(max = UserEmail.MAX_LENGTH)
        String email,
        @NotBlank @ValidRawPassword
        String password,

        @NotBlank
        @Size(min = Username.MIN_LENGTH, max = Username.MAX_LENGTH)
        @Pattern(regexp = Username.PATTERN_VALUE)
        String username
) {
}
