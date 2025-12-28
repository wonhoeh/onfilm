package com.onfilm.domain.movie.entity;

import com.onfilm.domain.user.entity.User;
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

    @OneToOne(mappedBy = "person", fetch = FetchType.LAZY)
    private User user;

    @Builder(access = AccessLevel.PRIVATE)
    private Person(
            String name,
            LocalDate birthDate,
            String birthPlace,
            String oneLineIntro,
            String profileImageUrl
    ) {
        this.name = name;
        this.birthDate = birthDate;
        this.birthPlace = birthPlace;
        this.oneLineIntro = oneLineIntro;
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * Person 생성 시 profileTags/snsList를 입력받고
     * 실제 컬렉션 세팅은 addXxx()를 통해서만 하게 해서
     * 태그 정규화/중복 제거/연관관계 설정 같은 규칙을 Person이 일관되게 보장
     */
    public static Person create(
            String name,
            LocalDate birthDate,
            String birthPlace,
            String oneLineIntro,
            String profileImageUrl,
            List<PersonSns> snsList,
            List<String> rawTags
    ) {
        Person person = Person.builder()
                .name(name)
                .birthDate(birthDate)
                .birthPlace(birthPlace)
                .oneLineIntro(oneLineIntro)
                .profileImageUrl(profileImageUrl)
                .build();

        if (snsList != null) snsList.forEach(person::addSns);
        if (rawTags != null) rawTags.forEach(person::addProfileTag);

        return person;
    }

    public void addSns(PersonSns sns) {
        if (sns == null) return;

        // JPA 엔티티는 저장 전 id가 없을 수 있어 비즈니스 키(type+url)로 중복 체크
        boolean duplicated = snsList.stream().anyMatch(s ->
                s.getType() == sns.getType() &&
                s.getUrl().equals(sns.getUrl())
        );
        if (duplicated) return;

        snsList.add(sns);
        sns.setPerson(this);
    }

    public void addProfileTag(String rawText) {
        if (rawText == null) return;

        String normalized = ProfileTag.normalize(rawText);
        if (normalized.isBlank()) return;

        boolean duplicated = profileTags.stream()
                .anyMatch(t -> t.getNormalized().equals(normalized));
        if (duplicated) return;

        ProfileTag tag = ProfileTag.from(this, rawText); // from에서 연관 관계 설정
        profileTags.add(tag);
    }

    public void removeProfileTag(String rawText) {
        if (rawText == null) return;
        String normalized = ProfileTag.normalize(rawText);

        profileTags.removeIf(t -> t.getNormalized().equals(normalized));
    }

    public void assignUser(User user) {
        this.user = user;
        if (user != null && user.getPerson() != this) {
            user.assignPerson(this);
        }
    }
    public void unassignUser() { this.user = null; }
}
