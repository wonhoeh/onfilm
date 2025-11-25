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

    private String name;
    private String profileImage;
    private LocalDate birthDate;

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PersonSns> snsList = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private Person(String name, String profileImage, LocalDate birthDate, List<PersonSns> snsList) {
        this.name = name;
        this.profileImage = profileImage;
        this.birthDate = birthDate;
        if (snsList != null) snsList.forEach(this::addSns);
    }

    public static Person create(CreatePersonRequest request, String profileImageUrl) {
        Person person = Person.builder()
                .name(request.getName())
                .birthDate(request.getBirthDate())
                .profileImage(profileImageUrl)
                .snsList(new ArrayList<>())
                .build();

        if (request.getSnsList() != null) {
            for (PersonSnsRequest snsReq : request.getSnsList()) {
                person.addSns(new PersonSns(snsReq.getType(), snsReq.getUrl()));
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
}
