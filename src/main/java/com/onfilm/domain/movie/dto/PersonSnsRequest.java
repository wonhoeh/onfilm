package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.SnsType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PersonSnsRequest {
    private SnsType type;
    private String url;
}
