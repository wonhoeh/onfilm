package com.onfilm.domain.movie.entity;

import com.onfilm.domain.common.TextNormalizer;
import com.onfilm.domain.common.error.exception.InvalidProfileTagException;
import com.onfilm.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Person {

    // ======================================================================
    // ======= 식별자 / 기본 컬럼 =======
    // ======================================================================

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

    // ======================================================================
    // ======= 연관관계: SNS =======
    // ======================================================================

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PersonSns> snsList = new ArrayList<>();

    // ======================================================================
    // ======= 연관관계: 프로필 태그 =======
    // ======================================================================

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProfileTag> profileTags = new ArrayList<>();

    // ======================================================================
    // ======= 연관관계: User =======
    // ======================================================================

    @OneToOne(mappedBy = "person", fetch = FetchType.LAZY)
    private User user;

    // ======================================================================
    // ======= 생성자 / 정적 팩토리 =======
    // ======================================================================

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

    // ======================================================================
    // ======= 연관관계 편의 메서드: SNS =======
    // ======================================================================

    public void addSns(PersonSns sns) {
        if (sns == null) return;

        // JPA 엔티티는 저장 전 id가 없을 수 있어 비즈니스 키(type+url)로 중복 체크
        boolean duplicated = snsList.stream().anyMatch(s ->
                s.getType() == sns.getType() &&
                        s.getUrl().equals(sns.getUrl())
        );
        if (duplicated) return;

        sns.setPerson(this);
        snsList.add(sns);
    }

    public void clearSns() {
        // 양방향 끊기 + orphanRemoval 삭제 유도
        for (PersonSns s : new ArrayList<>(snsList)) {
            s.setPerson(null);
        }
        snsList.clear();
    }

    public void replaceSns(List<PersonSns> newList) {
        clearSns();
        if (newList != null) newList.forEach(this::addSns);
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: 프로필 태그 =======
    // ======================================================================

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

    public void clearProfileTags() {
        // ProfileTag.from(this, ...)가 연관관계 설정해주니까 clear만 해도 됨
        // (ProfileTag가 person을 필수로 들고 있으면, 끊는 메서드가 있으면 더 좋음)
        profileTags.clear();
    }

    public void replaceProfileTags(List<String> rawTags) {
        List<String> input = (rawTags == null) ? List.of() : rawTags;

        // 1) 요청 태그를 "정규화 기준으로" 중복 제거
        // (같은 normalized면 최초 입력(rawText)만 유지)
        Map<String, String> normToRaw = new LinkedHashMap<>();
        for (String raw : input) {
            String cleaned = ProfileTag.validate(raw);              // blank 방지 + 길이 체크
            String normalized = TextNormalizer.textNormalizer(cleaned);

            // normalized가 비어있으면 스킵 (validate에서 거의 걸러지겠지만 안전장치)
            if (normalized.isBlank()) continue;

            // ⚠️ 컬럼이 length=30인데 validate는 40으로 되어있어서 불일치.
            // 아래에서 안전하게 막거나, validate max를 30으로 맞추는 걸 추천.
            if (normalized.length() > 30) {
                throw new InvalidProfileTagException("tag is too long (max 30)");
            }

            normToRaw.putIfAbsent(normalized, cleaned);
        }

        // 2) 기존 태그를 normalized로 인덱싱
        Map<String, ProfileTag> existing = new LinkedHashMap<>();
        for (ProfileTag tag : this.profileTags) {
            existing.putIfAbsent(tag.getNormalized(), tag);
        }

        // 3) 삭제: 요청에 없는 기존 태그 제거 (orphanRemoval이면 DB delete로 나감)
        this.profileTags.removeIf(tag -> !normToRaw.containsKey(tag.getNormalized()));

        // 4) 추가/유지: 없는 것만 새로 추가, 있는 건 rawText만 갱신(선택)
        for (Map.Entry<String, String> e : normToRaw.entrySet()) {
            String normalized = e.getKey();
            String cleanedRaw = e.getValue();

            ProfileTag tag = existing.get(normalized);
            if (tag == null) {
                this.profileTags.add(ProfileTag.from(this, cleanedRaw));
            } else {
                // 표시용(rawText) 업데이트가 필요하면 아래 메서드 추가해서 호출
                tag.updateRawTextKeepingNormalized(cleanedRaw);
            }
        }
    }

    // ======================================================================
    // ======= 기본정보 변경 메서드 =======
    // ======================================================================

    public void updateBasic(
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

    // ======================================================================
    // ======= 연관관계 편의 메서드: User =======
    // ======================================================================

    public void attachUser(User user) {
        this.user = user;
        if (user != null && user.getPerson() != this) {
            user.attachPerson(this);
        }
    }

    public void detachUser() {
        this.user = null;
    }
}
