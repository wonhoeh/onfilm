package com.onfilm.domain.movie.dto;

import lombok.Getter;

@Getter
public class CreateWriterRequest {
    private String name;
    private Integer age;
    private String sns;

    public CreateWriterRequest(String name, Integer age, String sns) {
        this.name = name;
        this.age = age;
        this.sns = sns;
    }
}
