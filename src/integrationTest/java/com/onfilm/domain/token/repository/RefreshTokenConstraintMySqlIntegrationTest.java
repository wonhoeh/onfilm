package com.onfilm.domain.token.repository;

import com.onfilm.domain.token.entity.RefreshToken;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.domain.user.repository.UserRepository;
import com.onfilm.support.MySqlContainerSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenConstraintMySqlIntegrationTest extends MySqlContainerSupport {

    private static final Instant ISSUED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(3600);

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void tokenHashUsesAsciiBinaryCollation() {
        User user = userRepository.saveAndFlush(
                createUser("hash@example.com", "hash-user")
        );
        String lowercaseHash = "a" + "0".repeat(RefreshToken.TOKEN_HASH_LENGTH - 1);
        String uppercaseHash = "A" + "0".repeat(RefreshToken.TOKEN_HASH_LENGTH - 1);

        RefreshToken lowercase = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(user.getId(), lowercaseHash, ISSUED_AT, EXPIRES_AT)
        );
        RefreshToken uppercase = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(user.getId(), uppercaseHash, ISSUED_AT, EXPIRES_AT)
        );

        assertThat(lowercase.getId()).isNotEqualTo(uppercase.getId());
        assertThat(refreshTokenRepository.findByTokenHash(lowercaseHash)).contains(lowercase);
        assertThat(refreshTokenRepository.findByTokenHash(uppercaseHash)).contains(uppercase);
        assertThat(jdbcTemplate.queryForObject("""
                        select collation_name
                          from information_schema.columns
                         where table_schema = database()
                           and table_name = 'refresh_tokens'
                           and column_name = 'token_hash'
                        """, String.class))
                .isEqualTo("ascii_bin");
    }

    @Test
    void foreignKeyRejectsRefreshTokenForUnknownUser() {
        RefreshToken orphan = RefreshToken.issue(
                Long.MAX_VALUE,
                "b".repeat(RefreshToken.TOKEN_HASH_LENGTH),
                ISSUED_AT,
                EXPIRES_AT
        );

        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingUserCascadesOwnedRefreshTokens() {
        User user = userRepository.saveAndFlush(
                createUser("delete-token@example.com", "delete-token-user")
        );
        RefreshToken token = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(
                        user.getId(),
                        "c".repeat(RefreshToken.TOKEN_HASH_LENGTH),
                        ISSUED_AT,
                        EXPIRES_AT
                )
        );
        Long tokenId = token.getId();

        userRepository.deleteById(user.getId());
        userRepository.flush();
        entityManager.clear();

        assertThat(refreshTokenRepository.findById(tokenId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
                        select delete_rule
                          from information_schema.referential_constraints
                         where constraint_schema = database()
                           and constraint_name = 'fk_refresh_tokens_user'
                        """, String.class))
                .isEqualTo("CASCADE");
    }

    private static User createUser(String email, String username) {
        User user = User.create(
                UserEmail.from(email),
                "encoded-password",
                Username.from(username)
        );
        user.createPerson(username);
        return user;
    }
}
