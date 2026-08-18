package com.onfilm.domain.movie.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryboardProjectTest {

    @Test
    void 제목을_정규화하고_필수값과_길이를_검증한다() {
        StoryboardProject project = StoryboardProject.create("  새 프로젝트  ");

        assertThat(project.getTitle()).isEqualTo("새 프로젝트");
        assertThat(StoryboardProject.create("a".repeat(120)).getTitle())
                .hasSize(120);
        assertThatThrownBy(() -> StoryboardProject.create(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StoryboardProject.create(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StoryboardProject.create("a".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> project.changeTitle(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 씬을_추가하고_삭제할_때_양방향_연관관계를_동기화한다() {
        StoryboardProject project = StoryboardProject.create("프로젝트");
        StoryboardScene scene = new StoryboardScene("씬", "대본");

        project.addScene(scene);

        assertThat(project.getScenes()).containsExactly(scene);
        assertThat(scene.getProject()).isSameAs(project);
        assertThatThrownBy(() -> project.getScenes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> project.addScene(scene))
                .isInstanceOf(IllegalArgumentException.class);

        project.removeScene(scene);

        assertThat(project.getScenes()).isEmpty();
        assertThat(scene.getProject()).isNull();
        assertThatThrownBy(() -> project.removeScene(scene))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 이미_다른_프로젝트에_속한_씬은_재할당할_수_없다() {
        StoryboardProject first = StoryboardProject.create("첫 프로젝트");
        StoryboardProject second = StoryboardProject.create("두 번째 프로젝트");
        StoryboardScene scene = new StoryboardScene("씬", null);
        first.addScene(scene);

        assertThatThrownBy(() -> second.addScene(scene))
                .isInstanceOf(IllegalStateException.class);
        assertThat(first.getScenes()).containsExactly(scene);
        assertThat(second.getScenes()).isEmpty();
    }

    @Test
    void 저장된_모든_씬_ID를_정확히_한_번씩_받아_재정렬한다() {
        StoryboardProject project = projectWithSavedScenes();

        project.reorderScenes(List.of(3L, 1L, 2L));

        assertThat(project.getScenes())
                .extracting(StoryboardScene::getId)
                .containsExactly(3L, 1L, 2L);
    }

    @Test
    void 잘못된_재정렬_요청은_상태를_변경하지_않고_거부한다() {
        StoryboardProject project = projectWithSavedScenes();

        assertInvalidOrder(project, List.of(3L, 1L));
        assertInvalidOrder(project, List.of(3L, 3L, 1L));
        assertInvalidOrder(project, List.of(3L, 999L, 1L));
        assertInvalidOrder(project, Arrays.asList(3L, null, 1L));
        assertInvalidOrder(project, null);
    }

    @Test
    void 저장되지_않은_씬은_ID로_재정렬할_수_없다() {
        StoryboardProject project = StoryboardProject.create("프로젝트");
        project.addScene(new StoryboardScene("씬", null));

        assertThatThrownBy(() -> project.reorderScenes(List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static StoryboardProject projectWithSavedScenes() {
        StoryboardProject project = StoryboardProject.create("프로젝트");
        for (long id = 1; id <= 3; id++) {
            StoryboardScene scene = new StoryboardScene("씬 " + id, null);
            setId(scene, id);
            project.addScene(scene);
        }
        return project;
    }

    private static void assertInvalidOrder(
            StoryboardProject project,
            List<Long> requestedIds
    ) {
        assertThatThrownBy(() -> project.reorderScenes(requestedIds))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(project.getScenes())
                .extracting(StoryboardScene::getId)
                .containsExactly(1L, 2L, 3L);
    }

    private static void setId(StoryboardScene scene, Long id) {
        try {
            Field field = StoryboardScene.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(scene, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
