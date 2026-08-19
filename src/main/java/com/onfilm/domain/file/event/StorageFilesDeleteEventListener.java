package com.onfilm.domain.file.event;

import com.onfilm.domain.file.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageFilesDeleteEventListener {

    private final StorageService storageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteFiles(StorageFilesDeleteEvent event) {
        for (String key : event.keys()) {
            try {
                storageService.delete(key);
            } catch (Exception exception) {
                log.error("Failed to delete storage file after transaction commit. key={}", key, exception);
            }
        }
    }
}
