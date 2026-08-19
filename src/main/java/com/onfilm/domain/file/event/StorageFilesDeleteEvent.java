package com.onfilm.domain.file.event;

import java.util.List;

public record StorageFilesDeleteEvent(List<String> keys) {

    public StorageFilesDeleteEvent {
        if (keys == null) {
            throw new IllegalArgumentException("keys is required");
        }
        keys = List.copyOf(keys);
    }
}
