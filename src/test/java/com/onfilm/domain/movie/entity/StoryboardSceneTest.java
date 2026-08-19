package com.onfilm.domain.movie.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryboardSceneTest {

    @Test
    void projectCreatesSceneAndNormalizesOptionalTitle() {
        StoryboardProject project = StoryboardProject.create("프로젝트");

        StoryboardScene scene = project.addScene(
                "  첫 번째 씬  ",
                "  <p>원본 대본</p>  "
        );
        StoryboardScene untitled = project.addScene("   ", null);

        assertThat(scene.getTitle()).isEqualTo("첫 번째 씬");
        assertThat(scene.getScriptHtml()).isEqualTo("  <p>원본 대본</p>  ");
        assertThat(scene.getProject()).isSameAs(project);
        assertThat(untitled.getTitle()).isNull();
        assertThat(project.getScenes()).containsExactly(scene, untitled);
    }

    @Test
    void titleAllows120CharactersAndRejects121WithoutPartialChange() {
        StoryboardProject project = StoryboardProject.create("프로젝트");
        StoryboardScene scene = project.addScene("a".repeat(120), "원본 대본");

        assertThat(scene.getTitle()).hasSize(120);
        assertThatThrownBy(() -> scene.changeContent("a".repeat(121), "변경 대본"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(scene.getTitle()).hasSize(120);
        assertThat(scene.getScriptHtml()).isEqualTo("원본 대본");
    }

    @Test
    void cardCreationRemovalAndReadOnlyCollectionKeepBothSidesInSync() {
        StoryboardProject project = StoryboardProject.create("프로젝트");
        StoryboardScene scene = project.addScene("씬", null);
        StoryboardScene otherScene = project.addScene("다른 씬", null);

        StoryboardCard card = scene.addCard("  storyboard/image.jpg  ");
        StoryboardCard blankCard = scene.addCard("   ");

        assertThat(card.getImageKey()).isEqualTo("storyboard/image.jpg");
        assertThat(blankCard.getImageKey()).isNull();
        assertThat(card.getScene()).isSameAs(scene);
        assertThatThrownBy(() -> scene.getCards().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> otherScene.addCard(card))
                .isInstanceOf(IllegalStateException.class);

        scene.removeCard(card);

        assertThat(card.getScene()).isNull();
        assertThat(scene.getCards()).containsExactly(blankCard);
    }

    @Test
    void imageKeyAllows512CharactersAndRejects513() {
        StoryboardScene scene = StoryboardProject.create("프로젝트")
                .addScene("씬", null);

        assertThat(scene.addCard("a".repeat(512)).getImageKey()).hasSize(512);
        assertThatThrownBy(() -> scene.addCard("a".repeat(513)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replaceCardsReusesAddsRemovesAndReordersCards() {
        StoryboardScene scene = savedSceneWithCards();
        StoryboardCard first = scene.getCards().get(0);
        StoryboardCard second = scene.getCards().get(1);
        StoryboardCard third = scene.getCards().get(2);

        StoryboardScene.CardReplacementResult result = scene.replaceCards(List.of(
                new StoryboardScene.CardChange(3L, "third-new.jpg"),
                new StoryboardScene.CardChange(1L, "first.jpg"),
                new StoryboardScene.CardChange(null, null)
        ));

        assertThat(scene.getCards()).extracting(StoryboardCard::getId)
                .containsExactly(3L, 1L, null);
        assertThat(scene.getCards()).extracting(StoryboardCard::getImageKey)
                .containsExactly("third-new.jpg", "first.jpg", null);
        assertThat(scene.getCards().get(0)).isSameAs(third);
        assertThat(scene.getCards().get(1)).isSameAs(first);
        assertThat(second.getScene()).isNull();
        assertThat(result.obsoleteImageKeys())
                .containsExactly("third.jpg", "second.jpg");
        assertThatThrownBy(() -> result.obsoleteImageKeys().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalidReplacementDoesNotChangeCards() {
        StoryboardScene scene = savedSceneWithCards();
        StoryboardScene otherScene = StoryboardProject.create("다른 프로젝트")
                .addScene("다른 씬", null);
        StoryboardCard otherCard = otherScene.addCard("other.jpg");
        setId(otherCard, 4L);

        assertInvalidReplacement(scene, List.of(
                new StoryboardScene.CardChange(1L, "first.jpg"),
                new StoryboardScene.CardChange(1L, "duplicate.jpg")
        ));
        assertInvalidReplacement(scene, List.of(
                new StoryboardScene.CardChange(999L, "unknown.jpg")
        ));
        assertInvalidReplacement(scene, List.of(
                new StoryboardScene.CardChange(otherCard.getId(), "other.jpg")
        ));
        assertInvalidReplacement(scene, Arrays.asList(
                new StoryboardScene.CardChange(1L, "first.jpg"),
                null
        ));
        assertThatThrownBy(() -> scene.replaceCards(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StoryboardScene savedSceneWithCards() {
        StoryboardScene scene = StoryboardProject.create("프로젝트")
                .addScene("씬", null);
        StoryboardCard first = scene.addCard("first.jpg");
        StoryboardCard second = scene.addCard("second.jpg");
        StoryboardCard third = scene.addCard("third.jpg");
        setId(first, 1L);
        setId(second, 2L);
        setId(third, 3L);
        return scene;
    }

    private static void assertInvalidReplacement(
            StoryboardScene scene,
            List<StoryboardScene.CardChange> changes
    ) {
        assertThatThrownBy(() -> scene.replaceCards(changes))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(scene.getCards()).extracting(StoryboardCard::getId)
                .containsExactly(1L, 2L, 3L);
        assertThat(scene.getCards()).extracting(StoryboardCard::getImageKey)
                .containsExactly("first.jpg", "second.jpg", "third.jpg");
    }

    private static void setId(StoryboardCard card, Long id) {
        try {
            Field field = StoryboardCard.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(card, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
