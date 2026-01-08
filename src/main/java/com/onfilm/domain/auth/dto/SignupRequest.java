package com.onfilm.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email @NotBlank
        String email,
        @NotBlank @Size(min = 8, max = 72)
        String password,

        // ✅ 3~20, 영문/숫자/_/-
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,20}$",
                message = "username은 3~20자, 영문/숫자/_/-만 가능")
        String username
) {
}
