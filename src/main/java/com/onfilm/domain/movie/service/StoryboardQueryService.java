package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardProjectNotFoundException;
import com.onfilm.domain.movie.dto.StoryboardProjectSummaryResponse;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.StoryboardProject;
import com.onfilm.domain.movie.entity.StoryboardScene;
import com.onfilm.domain.movie.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoryboardQueryService {

    private final PersonRepository personRepository;

    public List<StoryboardProjectSummaryResponse> findProjectsByPublicId(String publicId) {
        Person person = personRepository.findByPublicIdWithStoryboards(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        List<StoryboardProjectSummaryResponse> responses = new ArrayList<>();
        for (StoryboardProject project : person.getStoryboardProjects()) {
            responses.add(new StoryboardProjectSummaryResponse(
                    project.getId(),
                    project.getTitle(),
                    extractPreview(project),
                    project.getScenes().size()
            ));
        }
        return responses;
    }

    public StoryboardProject findProjectByPublicId(String publicId, Long projectId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        StoryboardProject project = person.getStoryboardProjects().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), projectId))
                .findFirst()
                .orElseThrow(() -> new StoryboardProjectNotFoundException(projectId));

        initializeProject(project);
        return project;
    }

    private String extractPreview(StoryboardProject project) {
        for (StoryboardScene scene : project.getScenes()) {
            String script = scene.getScriptHtml();
            if (script == null || script.isBlank()) {
                continue;
            }

            String cleaned = script.replaceAll("<[^>]*>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!cleaned.isBlank()) {
                return cleaned.length() > 160
                        ? cleaned.substring(0, 160) + "…"
                        : cleaned;
            }
        }
        return "";
    }

    private void initializeProject(StoryboardProject project) {
        for (StoryboardScene scene : project.getScenes()) {
            scene.getCards().size();
        }
    }
}
