package com.onfilm.domain.movie.service;

import com.onfilm.domain.file.event.StorageFileDeletionPublisher;
import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.entity.Person;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(PersonMediaTransactionBoundaryTest.TestConfig.class)
class PersonMediaTransactionBoundaryTest {

    @Autowired
    private PersonMediaService service;

    @Autowired
    private CurrentPersonProvider currentPersonProvider;

    @Autowired
    private StorageService storageService;

    @Autowired
    private StorageKeyFactory storageKeyFactory;

    @Autowired
    private StorageFileDeletionPublisher deletionPublisher;

    @Test
    void storesFileOutsideTransactionAndMutatesPersonInsideTransaction() {
        String oldKey = "profile/1/old.jpg";
        String newKey = "profile/1/550e8400-e29b-41d4-a716-446655440000.jpg";
        MultipartFile file = mock(MultipartFile.class);
        Person person = mock(Person.class);
        given(file.getOriginalFilename()).willReturn("profile.jpg");
        given(currentPersonProvider.getRequiredId()).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return 1L;
        });
        given(currentPersonProvider.getRequired()).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return person;
        });
        given(person.getProfileImageKey()).willReturn(oldKey);
        given(storageKeyFactory.profileAvatar(1L, ".jpg")).willReturn(newKey);
        given(storageService.save(newKey, file)).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return newKey;
        });
        given(storageService.toPublicUrl(newKey)).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return "https://cdn.example/" + newKey;
        });
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(deletionPublisher).publish(oldKey);

        service.replaceProfileImage(file);

        verify(person).changeProfileImageKey(newKey);
        verify(deletionPublisher).publish(oldKey);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        CurrentPersonProvider currentPersonProvider() {
            return mock(CurrentPersonProvider.class);
        }

        @Bean
        StorageService storageService() {
            return mock(StorageService.class);
        }

        @Bean
        StorageKeyFactory storageKeyFactory() {
            return mock(StorageKeyFactory.class);
        }

        @Bean
        StorageFileDeletionPublisher deletionPublisher() {
            return mock(StorageFileDeletionPublisher.class);
        }

        @Bean
        PersonMediaTransactionService transactionService(
                CurrentPersonProvider currentPersonProvider,
                StorageFileDeletionPublisher deletionPublisher
        ) {
            return new PersonMediaTransactionService(
                    currentPersonProvider,
                    deletionPublisher
            );
        }

        @Bean
        PersonMediaService personMediaService(
                PersonMediaTransactionService transactionService,
                StorageService storageService,
                StorageKeyFactory storageKeyFactory
        ) {
            return new PersonMediaService(
                    transactionService,
                    storageService,
                    storageKeyFactory
            );
        }

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:person-media-boundary-test;DB_CLOSE_DELAY=-1",
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
