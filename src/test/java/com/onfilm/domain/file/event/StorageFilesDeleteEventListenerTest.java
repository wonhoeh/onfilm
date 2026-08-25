package com.onfilm.domain.file.event;

import com.onfilm.domain.file.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(StorageFilesDeleteEventListenerTest.TestConfig.class)
class StorageFilesDeleteEventListenerTest {

    @Autowired
    private StorageFileDeletionPublisher deletionPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        reset(storageService);
    }

    @Test
    void deletesFilesOnlyAfterTransactionCommit() {
        String key = "storyboard/1/550e8400-e29b-41d4-a716-446655440000.jpg";

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            deletionPublisher.publish(key);

            verifyNoInteractions(storageService);
        });

        verify(storageService).delete(key);
    }

    @Test
    void doesNotDeleteFilesWhenTransactionRollsBack() {
        String key = "storyboard/1/550e8400-e29b-41d4-a716-446655440000.jpg";

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            deletionPublisher.publish(key);
            status.setRollbackOnly();
        });

        verify(storageService, never()).delete(key);
    }

    @Test
    void continuesDeletingRemainingFilesWhenOneDeletionFails() {
        String failedKey = "storyboard/1/550e8400-e29b-41d4-a716-446655440000.jpg";
        String nextKey = "storyboard/1/6ba7b810-9dad-41d1-80b4-00c04fd430c8.jpg";
        doThrow(new IllegalStateException("storage unavailable"))
                .when(storageService)
                .delete(failedKey);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                deletionPublisher.publish(List.of(failedKey, nextKey))
        );

        verify(storageService).delete(failedKey);
        verify(storageService).delete(nextKey);
    }

    @Test
    void rejectsDeletionRequestOutsideTransaction() {
        String key = "storyboard/1/550e8400-e29b-41d4-a716-446655440000.jpg";

        assertThatThrownBy(() -> deletionPublisher.publish(key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("storage file deletion must be published within an active transaction");

        verifyNoInteractions(storageService);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        StorageService storageService() {
            return mock(StorageService.class);
        }

        @Bean
        StorageFilesDeleteEventListener storageFilesDeleteEventListener(
                StorageService storageService
        ) {
            return new StorageFilesDeleteEventListener(storageService);
        }

        @Bean
        StorageFileDeletionPublisher storageFileDeletionPublisher(
                ApplicationEventPublisher eventPublisher
        ) {
            return new StorageFileDeletionPublisher(eventPublisher);
        }

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:storage-event-test;DB_CLOSE_DELAY=-1",
                    "sa",
                    ""
            );
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
