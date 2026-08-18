package com.onfilm.domain.movie.entity;

import com.onfilm.domain.common.error.exception.InvalidProfileTagException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "profile_tag",
        uniqueConstraints = @UniqueConstraint(name = "uk_person_tag", columnNames = {"person_id", "normalized"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileTag {
    static final int MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(nullable = false, length = MAX_LENGTH)
    private String rawText;         // 사용자가 입력한 원문 (표시용)

    @Column(nullable = false, length = MAX_LENGTH)
    private String normalized;      // 검색/중복 방지용 (공백정리, 소문자 등)

    private ProfileTag(TagValue value) {
        this.rawText = value.rawText();
        this.normalized = value.normalized();
    }

    static ProfileTag create(String rawText) {
        return new ProfileTag(sanitize(rawText));
    }

    void attachPerson(Person person) {
        if (person == null) {
            throw new IllegalArgumentException("person is required");
        }
        if (this.person != null && this.person != person) {
            throw new IllegalStateException(
                    "profileTag already belongs to another person"
            );
        }
        this.person = person;
    }

    void detachPerson(Person person) {
        if (this.person == person) {
            this.person = null;
        }
    }

    static String normalize(String rawText) {
        return sanitize(rawText).normalized();
    }

    void changeRawText(String rawText) {
        TagValue value = sanitize(rawText);
        if (!Objects.equals(this.normalized, value.normalized())) {
            throw new InvalidProfileTagException("normalized mismatch");
        }
        this.rawText = value.rawText();
    }

    private static TagValue sanitize(String rawText) {
        if (rawText == null) {
            throw new InvalidProfileTagException("tag is required");
        }

        String displayText = Normalizer.normalize(rawText.trim(), Normalizer.Form.NFKC);
        displayText = displayText.replaceFirst("^#+", "").trim();
        displayText = displayText.replaceAll("\\s+", " ");

        if (displayText.isBlank()) {
            throw new InvalidProfileTagException("tag must not be blank");
        }
        if (displayText.length() > MAX_LENGTH) {
            throw new InvalidProfileTagException(
                    "tag is too long (max " + MAX_LENGTH + ")"
            );
        }

        String normalized = displayText.toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_LENGTH) {
            throw new InvalidProfileTagException(
                    "normalized tag is too long (max " + MAX_LENGTH + ")"
            );
        }
        return new TagValue(displayText, normalized);
    }

    private record TagValue(String rawText, String normalized) {}
}
