package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
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

    public void addPerson(Person person) {
        this.person = person;
    }
}
