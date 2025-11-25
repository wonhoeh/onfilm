package com.onfilm.domain.movie.dto;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class CreateDirectorRequest {
    private String name;
    private LocalDate birthDate;
    private String sns;

    public CreateDirectorRequest(String name, LocalDate birthDate, String sns) {
        this.name = name;
        this.birthDate = birthDate;
        this.sns = sns;
    }
}
