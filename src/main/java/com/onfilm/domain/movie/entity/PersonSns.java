package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonSns {
    private static final int URL_MAX_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SnsType type;

    @Column(nullable = false, length = URL_MAX_LENGTH)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    private PersonSns(SnsType type, String url) {
        this.type = type;
        this.url = url;
    }

    public static PersonSns create(SnsType type, String url) {
        if (type == null) {
            throw new IllegalArgumentException("sns type is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("sns url is required");
        }

        String normalizedUrl = url.trim();
        if (normalizedUrl.length() > URL_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "sns url is too long (max " + URL_MAX_LENGTH + ")"
            );
        }

        return new PersonSns(type, normalizedUrl);
    }

    void attachPerson(Person person) {
        if (person == null) {
            throw new IllegalArgumentException("person is required");
        }
        if (this.person != null && this.person != person) {
            throw new IllegalStateException(
                    "personSns already belongs to another person"
            );
        }
        this.person = person;
    }

    void detachPerson(Person person) {
        if (this.person == person) {
            this.person = null;
        }
    }
}
