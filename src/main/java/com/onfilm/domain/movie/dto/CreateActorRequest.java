package com.onfilm.domain.movie.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class CreateActorRequest {
    @NotNull
    private String name;
    private LocalDate birthDate;
    private String sns;

    public CreateActorRequest(String name, LocalDate birthDate, String sns) {
        this.name = name;
        this.birthDate = birthDate;
        this.sns = sns;
    }
}
