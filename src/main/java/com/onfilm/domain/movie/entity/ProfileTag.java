package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Entity
@Table(
        name = "profile_tag",
        uniqueConstraints = @UniqueConstraint(name = "uk_person_tag", columnNames = {"person_id", "normalized"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileTag {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(nullable = false, length = 30)
    private String rawText;         // 사용자가 입력한 원문 (표시용)

    @Column(nullable = false, length = 30)
    private String normalized;      // 검색/중복 방지용 (공백정리, 소문자 등)

    private ProfileTag(Person person, String rawText) {
        this.person = person;
        this.rawText = rawText;
        this.normalized = normalize(this.rawText);
    }

    public static ProfileTag from(Person person, String rawText) {
        if (person == null) throw new IllegalArgumentException("person is required");
        return new ProfileTag(person, rawText);
    }

    public static String normalize(String input) {
        if (input == null) return "";

        String t = input.trim();
        t = t.replaceAll("^#+", "");     // 앞 # 제거
        t = t.trim();                                     // # 제거 후 남은 공백 제거
        t = t.replaceAll("\\s+", " ");   // 공백 여러개 -> 1개
        t = t.toLowerCase();
        return t;
    }

    public String getNormalized() {
        return normalized;
    }

    static String validate(String rawText) {
        if (rawText == null) throw new IllegalArgumentException("tag is required");

        String t = rawText.trim();
        // "#"만 있는 입력 방지
        t = t.replaceAll("^#+", "").trim();

        if (t.isBlank()) throw new IllegalArgumentException("tag must not be blank");
        if (t.length() > 40) throw new IllegalArgumentException("tag is too long (max 40)");

        return t;
    }

    public void updateRawTextKeepingNormalized(String rawText) {
        String cleaned = validate(rawText);
        String n = normalize(cleaned);

        // 기존 normalized와 다르면 다른 태그로 취급해야 하니 막는 게 안전
        if (!Objects.equals(this.normalized, n)) {
            throw new IllegalArgumentException("normalized mismatch");
        }

        this.rawText = cleaned;
    }
}
