package com.onfilm.domain.movie.entity;

import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.PersonSnsRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Person {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    private LocalDate birthDate;

    @Column(length = 80)
    private String birthPlace;

    @Column(length = 120)
    private String oneLineIntro;

    @Column(length = 512)
    private String profileImageUrl;

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PersonSns> snsList = new ArrayList<>();

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProfileTag> profileTags = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private Person(
            String name,
            LocalDate birthDate,
            String birthPlace,
            String oneLineIntro,
            String profileImageUrl,
            List<PersonSns> snsList,
            List<ProfileTag> profileTags
    ) {
        this.name = name;
        this.birthDate = birthDate;
        this.birthPlace = birthPlace;
        this.oneLineIntro = oneLineIntro;
        this.profileImageUrl = profileImageUrl;
        if (snsList != null) snsList.forEach(this::addSns);
        if (profileTags != null) profileTags.forEach(this::addProfileTag);
    }

    public static Person create(
            String name,
            LocalDate birthDate,
            String birthPlace,
            String oneLineIntro,
            String profileImageUrl,
            List<PersonSns> snsList,
            List<ProfileTag> profileTags
    ) {
        Person person = Person.builder()
                .name(name)
                .birthDate(birthDate)
                .birthPlace(birthPlace)
                .oneLineIntro(oneLineIntro)
                .profileImageUrl(profileImageUrl)
                .snsList(snsList)
                .profileTags(profileTags)
                .build();

        if (snsList != null) {
            for (PersonSns sns : snsList) {
                person.addSns(sns);
            }
        }

        return person;
    }

    public void addSns(PersonSns sns) {
        if (!snsList.contains(sns)) {
            snsList.add(sns);
            sns.addPerson(this);
        }
    }

    public void addProfileTag(String rawText) {
        if (rawText == null) return;
        ProfileTag tag = ProfileTag.of(this, rawText);
        if (profileTags.stream().anyMatch(t -> t.getNormalized().equals(tag.getNormalized()))) return;
        profileTags.add(tag);
    }

    public void removeProfileTag(String rawText) {
        if (rawText == null) return;
        String normalized = ProfileTag.normalize(rawText);

        profileTags.removeIf(t -> t.getNormalized().equals(normalized));
    }
}
