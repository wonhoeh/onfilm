package com.onfilm.domain.file.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class StorageFileDeletionPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(Collection<String> keys) {
        List<String> targets = keys.stream()
                .filter(Objects::nonNull)
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
        if (!targets.isEmpty()) {
            eventPublisher.publishEvent(new StorageFilesDeleteEvent(targets));
        }
    }

    public void publish(String key) {
        publish(Collections.singletonList(key));
    }
}
