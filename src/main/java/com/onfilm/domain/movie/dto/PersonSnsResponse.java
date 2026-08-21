package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.PersonSns;
import com.onfilm.domain.movie.entity.SnsType;

public record PersonSnsResponse(
        SnsType type,
        String url
) {
    public static PersonSnsResponse from(PersonSns sns) {
        return new PersonSnsResponse(sns.getType(), sns.getUrl());
    }
}
