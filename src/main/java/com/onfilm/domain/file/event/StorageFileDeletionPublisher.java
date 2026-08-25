package com.onfilm.domain.file.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class StorageFileDeletionPublisher {

    private static final String TRANSACTION_REQUIRED_MESSAGE =
            "storage file deletion must be published within an active transaction";

    private final ApplicationEventPublisher eventPublisher;

    public void publish(Collection<String> keys) {
        List<String> targets = keys.stream()
                .filter(Objects::nonNull)
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
        if (!targets.isEmpty()) {
            requireActiveTransaction();
            eventPublisher.publishEvent(new StorageFilesDeleteEvent(targets));
        }
    }

    public void publish(String key) {
        publish(Collections.singletonList(key));
    }

    private void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(TRANSACTION_REQUIRED_MESSAGE);
        }
    }
}
