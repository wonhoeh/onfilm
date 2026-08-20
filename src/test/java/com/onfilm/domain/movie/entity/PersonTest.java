package com.onfilm.domain.movie.entity;

import com.onfilm.domain.common.error.exception.InvalidProfileTagException;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonTest {

    @Test
    void create_normalizesBasicInfoAndCreatesPublicIdImmediately() {
        Person person = Person.create(
                "  테스트 배우  ",
                LocalDate.of(1990, 1, 1),
                "  서울  ",
                "  한 줄 소개  ",
                "  profile/1/avatar.jpg  ",
                List.of(),
                List.of()
        );

        assertThat(person.getName()).isEqualTo("테스트 배우");
        assertThat(person.getBirthPlace()).isEqualTo("서울");
        assertThat(person.getOneLineIntro()).isEqualTo("한 줄 소개");
        assertThat(person.getProfileImageKey()).isEqualTo("profile/1/avatar.jpg");
        assertThat(person.getPublicId()).isNotBlank();
    }

    @Test
    void create_rejectsInvalidBasicInfo() {
        assertThatThrownBy(() -> createPerson("   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name is required");

        assertThatThrownBy(() -> createPerson(
                "테스트 배우",
                LocalDate.now().plusDays(1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("birthDate must not be in the future");
    }

    @Test
    void changeBasicInfo_validatesAllValuesBeforeChangingState() {
        Person person = createPerson();

        assertThatThrownBy(() -> person.changeBasicInfo(
                "변경된 이름",
                null,
                null,
                "a".repeat(121),
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("oneLineIntro is too long (max 120)");

        assertThat(person.getName()).isEqualTo("테스트 배우");
    }

    @Test
    void profileTag_usesSingleNormalizationPolicyAndRejectsLongText() {
        Person person = createPerson();

        person.addProfileTag("  #Action   Film  ");
        person.addProfileTag("Ａｃｔｉｏｎ Film");
        person.addProfileTag("\u1100\u1161");
        person.addProfileTag("가");
        person.addProfileTag("b".repeat(30));
        person.addProfileTag("action");

        assertThat(person.getProfileTags()).hasSize(4);
        assertThat(person.getProfileTags().get(0).getRawText()).isEqualTo("Action Film");
        assertThat(person.getProfileTags().get(0).getNormalized()).isEqualTo("action film");
        assertThat(person.getProfileTags().get(1).getRawText()).isEqualTo("가");
        assertThat(person.getProfileTags().get(2).getRawText()).hasSize(30);

        assertThatThrownBy(() -> person.addProfileTag("a".repeat(31)))
                .isInstanceOf(InvalidProfileTagException.class)
                .hasMessage("tag is too long (max 30)");
    }

    @Test
    void profileTag_reusesExistingEntityAndAppliesRequestedOrder() {
        Person person = createPerson();
        person.addProfileTag("Action");
        person.addProfileTag("Drama");
        ProfileTag action = person.getProfileTags().get(0);
        ProfileTag drama = person.getProfileTags().get(1);

        person.replaceProfileTags(List.of("New Tag", "ACTION", "new   tag"));

        assertThat(person.getProfileTags())
                .extracting(ProfileTag::getRawText)
                .containsExactly("New Tag", "ACTION");
        assertThat(person.getProfileTags().get(1)).isSameAs(action);
        assertThat(action.getPerson()).isSameAs(person);
        assertThat(drama.getPerson()).isNull();
    }

    @Test
    void profileTag_rejectsMoreThanTwentyUniqueTagsWithoutChangingState() {
        Person person = createPerson();
        person.addProfileTag("existing");

        List<String> tooManyTags = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tooManyTags.add("tag-" + i);
        }

        assertThatThrownBy(() -> person.replaceProfileTags(tooManyTags))
                .isInstanceOf(InvalidProfileTagException.class)
                .hasMessage("too many tags (max 20)");
        assertThat(person.getProfileTags())
                .extracting(ProfileTag::getRawText)
                .containsExactly("existing");
    }

    @Test
    void sns_attachesBothSidesAndRejectsDuplicateOrReassignment() {
        Person person = createPerson();
        Person anotherPerson = createPerson();
        PersonSns sns = person.addSns(
                SnsType.INSTAGRAM,
                "  INSTAGRAM.COM/onfilm/  "
        );

        assertThat(sns.getPerson()).isSameAs(person);
        assertThat(sns.getUrl()).isEqualTo("https://instagram.com/onfilm");
        assertThat(person.getSnsList()).containsExactly(sns);

        assertThatThrownBy(() -> person.addSns(
                SnsType.ETC,
                "https://INSTAGRAM.COM:443/onfilm/"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate person sns");

        PersonSns anotherInstagram = person.addSns(
                SnsType.INSTAGRAM,
                "https://instagram.com/onfilm-official"
        );
        assertThat(person.getSnsList()).containsExactly(sns, anotherInstagram);

        assertThatThrownBy(() -> anotherPerson.addSns(sns))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("personSns already belongs to another person");
    }

    @Test
    void sns_rejectsUnsupportedSchemeOrMissingHost() {
        assertThatThrownBy(() -> PersonSns.create(
                SnsType.ETC,
                "ftp://example.com/profile"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sns url must use http or https");

        assertThatThrownBy(() -> PersonSns.create(
                SnsType.ETC,
                "https:///profile"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sns url host is required");
    }

    @Test
    void collections_areExposedAsReadOnlyViews() {
        Person person = createPerson();

        assertThatThrownBy(() -> person.getSnsList().add(
                PersonSns.create(SnsType.YOUTUBE, "https://youtube.com/onfilm")
        )).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> person.getProfileTags().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> person.getGalleryItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> person.getStoryboardProjects().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void storyboardProject_attachesAndDetachesThroughPerson() {
        Person person = createPerson();
        StoryboardProject project = person.addStoryboardProject("  새 프로젝트  ");

        assertThat(project.getTitle()).isEqualTo("새 프로젝트");
        assertThat(project.getPerson()).isSameAs(person);
        assertThat(person.getStoryboardProjects()).containsExactly(project);

        person.removeStoryboardProject(project);

        assertThat(project.getPerson()).isNull();
        assertThat(person.getStoryboardProjects()).isEmpty();
    }

    @Test
    void gallery_enforcesUniqueKeysAndExactReordering() {
        Person person = createPerson();
        person.addGalleryImageKey("  gallery/first.jpg  ");
        person.addGalleryImageKey("gallery/second.jpg");

        assertThatThrownBy(() -> person.addGalleryImageKey("gallery/first.jpg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate gallery image key");
        assertThatThrownBy(() -> person.reorderGallery(List.of("gallery/first.jpg")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("orderedKeys must contain every gallery image exactly once");

        person.reorderGallery(List.of("gallery/second.jpg", "gallery/first.jpg"));
        person.changeGalleryItemPrivacy("gallery/second.jpg", true);

        assertThat(person.getGalleryItems())
                .extracting(Person.GalleryItem::getKey)
                .containsExactly("gallery/second.jpg", "gallery/first.jpg");
        assertThat(person.getGalleryItems().get(0).isPrivate()).isTrue();
    }

    @Test
    void userAssociation_rejectsReassignmentAndKeepsRequiredAssociation() {
        Person person = createPerson();
        User user = User.create(
                UserEmail.from("first@test.com"),
                "encoded-password",
                Username.from("first-user")
        );
        User anotherUser = User.create(
                UserEmail.from("second@test.com"),
                "encoded-password",
                Username.from("second-user")
        );

        user.attachPerson(person);

        assertThat(user.getPerson()).isSameAs(person);
        assertThat(person.getUser()).isSameAs(user);
        assertThatThrownBy(() -> anotherUser.attachPerson(person))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("person already belongs to another user");

    }

    private static Person createPerson() {
        return createPerson("테스트 배우", null);
    }

    private static Person createPerson(String name, LocalDate birthDate) {
        return Person.create(
                name,
                birthDate,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }
}
