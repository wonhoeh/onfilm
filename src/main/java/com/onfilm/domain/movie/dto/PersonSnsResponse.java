package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.PersonSns;
import com.onfilm.domain.movie.entity.SnsType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonSnsResponse {
    private SnsType type;
    private String url;

    public static PersonSnsResponse from(PersonSns sns) {
        return PersonSnsResponse.builder()
                .type(sns.getType())
                .url(sns.getUrl())
                .build();
    }
}
