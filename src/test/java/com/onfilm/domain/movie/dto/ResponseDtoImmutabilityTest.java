package com.onfilm.domain.movie.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseDtoImmutabilityTest {
    @Test
    void filmographyResponseCopiesInputList() {
        List<FilmographyUpsertResponse.Item> source = new ArrayList<>();
        source.add(new FilmographyUpsertResponse.Item("client-1", 1L));

        FilmographyUpsertResponse response = new FilmographyUpsertResponse(source);
        source.clear();

        assertThat(response.items()).hasSize(1);
        assertThatThrownBy(() -> response.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void storyboardResponsesExposeImmutableLists() {
        StoryboardSceneResponse scene = new StoryboardSceneResponse(1L, "씬", null, 0, List.of());
        StoryboardProjectResponse project = new StoryboardProjectResponse(1L, "프로젝트", List.of(scene));

        assertThatThrownBy(() -> scene.cards().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> project.scenes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
