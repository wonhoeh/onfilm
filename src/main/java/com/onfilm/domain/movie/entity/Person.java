package com.onfilm.domain.movie.entity;

import com.onfilm.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Person {
    private static final int NAME_MAX_LENGTH = 60;
    private static final int BIRTH_PLACE_MAX_LENGTH = 80;
    private static final int ONE_LINE_INTRO_MAX_LENGTH = 120;
    private static final int STORAGE_KEY_MAX_LENGTH = 512;

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

    @Column(name = "profile_image_url", length = 512)
    private String profileImageKey;

    @Column(length = 512)
    private String filmographyFileKey;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String publicId;  // UUID

    @Column(nullable = false)
    private boolean filmographyPrivate = false;

    @Column(nullable = false)
    private boolean galleryPrivate = false;

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
    // ======= 연관관계: Gallery =======
    // ======================================================================

    @ElementCollection
    @CollectionTable(
            name = "person_gallery",
            joinColumns = @JoinColumn(name = "person_id")
    )
    @OrderColumn(name = "sort_order") // ✅ 순서 보존(드래그 정렬 가능)
    private List<GalleryItem> galleryItems = new ArrayList<>();

    // ======================================================================
    // ======= 연관관계: Storyboard =======
    // ======================================================================

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "sort_order")
    private List<StoryboardProject> storyboardProjects = new ArrayList<>();

    // ======================================================================
    // ======= 생성자 / 정적 팩토리 =======
    // ======================================================================
    @PrePersist
    private void prePersist() {
        if (this.publicId == null || this.publicId.isBlank()) {
            this.publicId = UUID.randomUUID().toString();
        }
    }

    private Person(
            String name,
            LocalDate birthDate,
            String birthPlace,
            String oneLineIntro,
            String profileImageKey
    ) {
        this.publicId = UUID.randomUUID().toString();
        applyBasicInfo(name, birthDate, birthPlace, oneLineIntro, profileImageKey);
    }

    private void applyBasicInfo(
            String name,
            LocalDate birthDate,
            String birthPlace,
            String oneLineIntro,
            String profileImageKey
    ) {
        String validatedName = requireText(name, "name", NAME_MAX_LENGTH);
        LocalDate validatedBirthDate = validateBirthDate(birthDate);
        String normalizedBirthPlace = normalizeOptionalText(
                birthPlace,
                "birthPlace",
                BIRTH_PLACE_MAX_LENGTH
        );
        String normalizedOneLineIntro = normalizeOptionalText(
                oneLineIntro,
                "oneLineIntro",
                ONE_LINE_INTRO_MAX_LENGTH
        );
        String normalizedProfileImageKey = normalizeOptionalText(
                profileImageKey,
                "profileImageKey",
                STORAGE_KEY_MAX_LENGTH
        );

        this.name = validatedName;
        this.birthDate = validatedBirthDate;
        this.birthPlace = normalizedBirthPlace;
        this.oneLineIntro = normalizedOneLineIntro;
        this.profileImageKey = normalizedProfileImageKey;
    }

    public static Person create(
            String name,
            LocalDate birthDate,
            String birthPlace,
            String oneLineIntro,
            String profileImageKey,
            List<PersonSns> snsList,
            List<String> rawTags
    ) {
        Person person = new Person(
                name,
                birthDate,
                birthPlace,
                oneLineIntro,
                profileImageKey
        );

        if (snsList != null) snsList.forEach(person::addSns);
        if (rawTags != null) rawTags.forEach(person::addProfileTag);

        return person;
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: SNS =======
    // ======================================================================

    public void addSns(PersonSns sns) {
        PersonSns requiredSns = require(sns, "sns");

        // JPA 엔티티는 저장 전 id가 없을 수 있어 비즈니스 키(type+url)로 중복 체크
        boolean duplicated = snsList.stream().anyMatch(s ->
                s.getType() == requiredSns.getType() &&
                        s.getUrl().equals(requiredSns.getUrl())
        );
        if (duplicated) {
            throw new IllegalArgumentException("duplicate person sns");
        }

        requiredSns.attachPerson(this);
        snsList.add(requiredSns);
    }

    public void clearSns() {
        // 양방향 끊기 + orphanRemoval 삭제 유도
        for (PersonSns s : new ArrayList<>(snsList)) {
            s.detachPerson(this);
        }
        snsList.clear();
    }

    public void replaceSns(List<PersonSns> newList) {
        List<PersonSns> replacements = (newList == null) ? List.of() : newList;
        validateSnsReplacements(replacements);
        clearSns();
        replacements.forEach(this::addSns);
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: 프로필 태그 =======
    // ======================================================================

    public void addProfileTag(String rawText) {
        ProfileTag tag = ProfileTag.create(this, rawText);

        boolean duplicated = profileTags.stream()
                .anyMatch(t -> t.getNormalized().equals(tag.getNormalized()));
        if (duplicated) return;

        profileTags.add(tag);
    }

    public void removeProfileTag(String rawText) {
        String cleaned = ProfileTag.validate(rawText);
        String normalized = ProfileTag.normalize(cleaned);
        profileTags.removeIf(t -> t.getNormalized().equals(normalized));
    }

    public void clearProfileTags() {
        profileTags.clear();
    }

    public void replaceProfileTags(List<String> rawTags) {
        List<String> input = (rawTags == null) ? List.of() : rawTags;

        // 1) 요청 태그를 "정규화 기준으로" 중복 제거
        // (같은 normalized면 최초 입력(rawText)만 유지)
        Map<String, String> normToRaw = new LinkedHashMap<>();
        for (String raw : input) {
            String cleaned = ProfileTag.validate(raw);
            String normalized = ProfileTag.normalize(cleaned);

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
                this.profileTags.add(ProfileTag.create(this, cleanedRaw));
            } else {
                tag.changeRawTextKeepingNormalized(cleanedRaw);
            }
        }
    }

    // ======================================================================
    // ======= 기본정보 변경 메서드 =======
    // ======================================================================

    public void changeBasicInfo(
            String name,
            LocalDate birthDate,
            String birthPlace,
            String oneLineIntro,
            String profileImageKey
    ) {
        applyBasicInfo(name, birthDate, birthPlace, oneLineIntro, profileImageKey);
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: User =======
    // ======================================================================

    public void attachUser(User user) {
        User requiredUser = require(user, "user");
        if (this.user != null && this.user != requiredUser) {
            throw new IllegalStateException("person already belongs to another user");
        }
        if (requiredUser.getPerson() != null && requiredUser.getPerson() != this) {
            throw new IllegalStateException("user already has another person");
        }

        this.user = requiredUser;
        if (requiredUser.getPerson() != this) {
            requiredUser.attachPerson(this);
        }
    }

    public void detachUser() {
        if (this.user == null) {
            return;
        }

        User oldUser = this.user;
        this.user = null;
        if (oldUser.getPerson() == this) {
            oldUser.detachPerson();
        }
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: Storyboard =======
    // ======================================================================

    public void addStoryboardProject(StoryboardProject project) {
        StoryboardProject requiredProject = require(project, "storyboardProject");
        if (storyboardProjects.contains(requiredProject)) {
            throw new IllegalArgumentException("duplicate storyboard project");
        }

        requiredProject.attachPerson(this);
        storyboardProjects.add(requiredProject);
    }

    public void removeStoryboardProject(StoryboardProject project) {
        StoryboardProject requiredProject = require(project, "storyboardProject");
        if (!storyboardProjects.remove(requiredProject)) {
            throw new IllegalArgumentException("storyboard project not found");
        }
        requiredProject.detachPerson(this);
    }

    // ======================================================================
    // ======= 편의 메서드: Gallery =======
    // ======================================================================

    public void addGalleryImageKey(String key) {
        String normalizedKey = requireText(key, "galleryImageKey", STORAGE_KEY_MAX_LENGTH);
        boolean duplicated = galleryItems.stream()
                .anyMatch(item -> item.getKey().equals(normalizedKey));
        if (duplicated) {
            throw new IllegalArgumentException("duplicate gallery image key");
        }

        galleryItems.add(GalleryItem.create(normalizedKey));
    }

    public void removeGalleryImageKey(String key) {
        String normalizedKey = requireText(key, "galleryImageKey", STORAGE_KEY_MAX_LENGTH);
        boolean removed = galleryItems.removeIf(item -> item.getKey().equals(normalizedKey));
        if (!removed) {
            throw new IllegalArgumentException("gallery image not found");
        }
    }

    public void reorderGallery(List<String> orderedKeys) {
        List<String> requiredKeys = require(orderedKeys, "orderedKeys").stream()
                .map(key -> requireText(key, "galleryImageKey", STORAGE_KEY_MAX_LENGTH))
                .toList();

        Set<String> uniqueKeys = new LinkedHashSet<>(requiredKeys);
        Set<String> existingKeys = galleryItems.stream()
                .map(GalleryItem::getKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (uniqueKeys.size() != requiredKeys.size() || !uniqueKeys.equals(existingKeys)) {
            throw new IllegalArgumentException(
                    "orderedKeys must contain every gallery image exactly once"
            );
        }

        Map<String, GalleryItem> byKey = new LinkedHashMap<>();
        for (GalleryItem item : galleryItems) {
            byKey.put(item.getKey(), item);
        }

        List<GalleryItem> reordered = new ArrayList<>();
        for (String key : requiredKeys) {
            reordered.add(byKey.get(key));
        }

        this.galleryItems.clear();
        this.galleryItems.addAll(reordered);
    }

    public void changeGalleryItemPrivacy(String key, boolean isPrivate) {
        String normalizedKey = requireText(key, "galleryImageKey", STORAGE_KEY_MAX_LENGTH);
        for (GalleryItem item : galleryItems) {
            if (item.getKey().equals(normalizedKey)) {
                item.changePrivacy(isPrivate);
                return;
            }
        }
        throw new IllegalArgumentException("gallery image not found");
    }

    // ======================================================================
    // ======= 파일 및 공개 범위 변경 메서드 =======
    // ======================================================================
    public void changeProfileImageKey(String key) {
        this.profileImageKey = normalizeOptionalText(
                key,
                "profileImageKey",
                STORAGE_KEY_MAX_LENGTH
        );
    }

    public void changeFilmographyFileKey(String key) {
        this.filmographyFileKey = normalizeOptionalText(
                key,
                "filmographyFileKey",
                STORAGE_KEY_MAX_LENGTH
        );
    }

    public void changeFilmographyPrivate(boolean value) { this.filmographyPrivate = value; }
    public void changeGalleryPrivate(boolean value) { this.galleryPrivate = value; }

    @Embeddable
    public static class GalleryItem {
        @Column(name = "image_key", nullable = false, length = 512)
        private String key;

        @Column(name = "is_private", nullable = false)
        private boolean isPrivate;

        protected GalleryItem() {}

        private GalleryItem(String key, boolean isPrivate) {
            this.key = key;
            this.isPrivate = isPrivate;
        }

        private static GalleryItem create(String key) {
            return new GalleryItem(key, false);
        }

        public String getKey() { return key; }
        public boolean isPrivate() { return isPrivate; }
        private void changePrivacy(boolean isPrivate) { this.isPrivate = isPrivate; }
    }

    public List<PersonSns> getSnsList() {
        return Collections.unmodifiableList(snsList);
    }

    public List<ProfileTag> getProfileTags() {
        return Collections.unmodifiableList(profileTags);
    }

    public List<GalleryItem> getGalleryItems() {
        return Collections.unmodifiableList(galleryItems);
    }

    public List<StoryboardProject> getStoryboardProjects() {
        return Collections.unmodifiableList(storyboardProjects);
    }

    private void validateSnsReplacements(List<PersonSns> replacements) {
        Set<String> businessKeys = new HashSet<>();
        for (PersonSns sns : replacements) {
            PersonSns requiredSns = require(sns, "sns");
            if (requiredSns.getPerson() != null && requiredSns.getPerson() != this) {
                throw new IllegalStateException(
                        "personSns already belongs to another person"
                );
            }

            String businessKey = requiredSns.getType() + "\u0000" + requiredSns.getUrl();
            if (!businessKeys.add(businessKey)) {
                throw new IllegalArgumentException("duplicate person sns");
            }
        }
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String requireText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        String trimmed = value.trim();
        validateLength(trimmed, fieldName, maxLength);
        return trimmed;
    }

    private static String normalizeOptionalText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        validateLength(trimmed, fieldName, maxLength);
        return trimmed;
    }

    private static void validateLength(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " is too long (max " + maxLength + ")"
            );
        }
    }

    private static LocalDate validateBirthDate(LocalDate birthDate) {
        if (birthDate != null && birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("birthDate must not be in the future");
        }
        return birthDate;
    }
}
