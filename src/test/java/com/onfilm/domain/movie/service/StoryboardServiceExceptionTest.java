package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardProjectNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardSceneNotFoundException;
import com.onfilm.domain.file.event.StorageFileDeletionPublisher;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.StoryboardProject;
import com.onfilm.domain.movie.repository.PersonRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class StoryboardServiceExceptionTest {

    @Test
    void commandThrowsProjectNotFoundWhenProjectDoesNotBelongToCurrentPerson() {
        CurrentPersonProvider currentPersonProvider = mock(CurrentPersonProvider.class);
        StoryboardCommandService service = commandService(currentPersonProvider);
        Person person = mock(Person.class);
        given(person.getStoryboardProjects()).willReturn(List.of());
        given(currentPersonProvider.getRequired("public-id")).willReturn(person);

        assertThatThrownBy(() -> service.deleteProject("public-id", 1L))
                .isInstanceOfSatisfying(StoryboardProjectNotFoundException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.STORYBOARD_PROJECT_NOT_FOUND));
    }

    @Test
    void commandThrowsSceneNotFoundWhenSceneDoesNotBelongToProject() {
        CurrentPersonProvider currentPersonProvider = mock(CurrentPersonProvider.class);
        StoryboardCommandService service = commandService(currentPersonProvider);
        Person person = mock(Person.class);
        StoryboardProject project = mock(StoryboardProject.class);
        given(project.getId()).willReturn(1L);
        given(project.getScenes()).willReturn(List.of());
        given(person.getStoryboardProjects()).willReturn(List.of(project));
        given(currentPersonProvider.getRequired("public-id")).willReturn(person);

        assertThatThrownBy(() -> service.deleteScene("public-id", 1L, 2L))
                .isInstanceOfSatisfying(StoryboardSceneNotFoundException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.STORYBOARD_SCENE_NOT_FOUND));
    }

    @Test
    void queryThrowsPersonNotFoundAfterOwnershipValidation() {
        PersonRepository personRepository = mock(PersonRepository.class);
        CurrentPersonProvider currentPersonProvider = mock(CurrentPersonProvider.class);
        StoryboardResponseMapper responseMapper = mock(StoryboardResponseMapper.class);
        StoryboardQueryService service = new StoryboardQueryService(
                personRepository,
                currentPersonProvider,
                responseMapper
        );
        given(personRepository.findByPublicId("public-id")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findProjectByPublicId("public-id", 1L))
                .isInstanceOfSatisfying(PersonNotFoundException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PERSON_NOT_FOUND));
    }

    private static StoryboardCommandService commandService(
            CurrentPersonProvider currentPersonProvider
    ) {
        return new StoryboardCommandService(
                currentPersonProvider,
                mock(StorageKeyPolicy.class),
                mock(StorageFileDeletionPublisher.class)
        );
    }
}
