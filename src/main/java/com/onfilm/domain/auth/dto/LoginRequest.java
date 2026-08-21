package com.onfilm.domain.auth.dto;

import com.onfilm.domain.user.entity.UserEmail;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = UserEmail.MAX_LENGTH)
        String email,
        @NotBlank
        String password
) {
}
