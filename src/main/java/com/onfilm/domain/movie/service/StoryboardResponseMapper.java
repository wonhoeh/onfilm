package com.onfilm.domain.movie.service;

import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.StoryboardCardResponse;
import com.onfilm.domain.movie.dto.StoryboardProjectResponse;
import com.onfilm.domain.movie.dto.StoryboardSceneResponse;
import com.onfilm.domain.movie.entity.StoryboardCard;
import com.onfilm.domain.movie.entity.StoryboardProject;
import com.onfilm.domain.movie.entity.StoryboardScene;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StoryboardResponseMapper {

    private final StorageService storageService;

    public StoryboardProjectResponse toProjectResponse(StoryboardProject project) {
        List<StoryboardSceneResponse> scenes = new ArrayList<>();
        for (int index = 0; index < project.getScenes().size(); index++) {
            scenes.add(toSceneResponse(project.getScenes().get(index), index + 1));
        }
        return new StoryboardProjectResponse(project.getId(), project.getTitle(), scenes);
    }

    public StoryboardSceneResponse toSceneResponse(StoryboardScene scene) {
        int index = scene.getProject() == null ? -1 : scene.getProject().getScenes().indexOf(scene);
        return toSceneResponse(scene, index < 0 ? 0 : index + 1);
    }

    private StoryboardSceneResponse toSceneResponse(StoryboardScene scene, int sortOrder) {
        List<StoryboardCardResponse> cards = new ArrayList<>();
        for (int index = 0; index < scene.getCards().size(); index++) {
            StoryboardCard card = scene.getCards().get(index);
            String key = card.getImageKey();
            String url = key == null || key.isBlank() ? null : storageService.toPublicUrl(key);
            cards.add(new StoryboardCardResponse(card.getId(), key, url, index + 1));
        }
        return new StoryboardSceneResponse(
                scene.getId(), scene.getTitle(), scene.getScriptHtml(), sortOrder, cards
        );
    }
}
