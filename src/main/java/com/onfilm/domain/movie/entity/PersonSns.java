package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonSns {

    @Id @GeneratedValue
    private Long id;

    @Enumerated(EnumType.STRING)
    private SnsType type; // instagrm, youtube, twitter
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Builder
    public PersonSns(SnsType type, String url) {
        this.type = type;
        this.url = url;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public static PersonSns create(SnsType type, String url) {
        if (type == null) {
            throw new IllegalArgumentException("sns type is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("sns url is required");
        }
        return PersonSns.builder()
                .type(type)
                .url(url)
                .build();
    }
}
