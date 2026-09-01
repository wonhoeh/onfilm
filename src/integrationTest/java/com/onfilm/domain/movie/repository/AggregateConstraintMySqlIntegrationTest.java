package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.CastType;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.PersonRole;
import com.onfilm.domain.movie.entity.StoryboardCard;
import com.onfilm.domain.movie.entity.StoryboardProject;
import com.onfilm.domain.movie.entity.StoryboardScene;
import com.onfilm.domain.movie.entity.Trailer;
import com.onfilm.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AggregateConstraintMySqlIntegrationTest extends MySqlContainerSupport {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @EnumSource(OrderTarget.class)
    void orderConstraintRejectsNegativeValue(OrderTarget target) {
        AggregateFixture fixture = saveAggregateFixture();

        assertThatThrownBy(() -> target.updateSortOrder(jdbcTemplate, fixture, -1))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining(target.constraintName);
    }

    @Test
    void aggregateOrderColumnsContainZeroAfterHibernateSynchronization() {
        AggregateFixture fixture = saveAggregateFixture();

        for (OrderTarget target : OrderTarget.values()) {
            assertThat(target.findSortOrder(jdbcTemplate, fixture))
                    .as(target.name())
                    .isZero();
        }
    }

    @Test
    void galleryUniqueConstraintRejectsDuplicateImageKeyForSamePerson() {
        AggregateFixture fixture = saveAggregateFixture();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        insert into person_gallery (
                            person_id, sort_order, image_key, is_private
                        ) values (?, ?, ?, ?)
                        """,
                fixture.personId(), 1, fixture.galleryImageKey(), false
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidMovieRanges")
    void movieCheckConstraintRejectsInvalidRange(MovieRange range) {
        AggregateFixture fixture = saveAggregateFixture();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update movie set " + range.column() + " = ? where movie_id = ?",
                range.value(),
                fixture.movieId()
        )).isInstanceOf(DataAccessException.class)
                .hasMessageContaining(range.constraintName());
    }

    @Test
    void movieCheckConstraintAcceptsBoundaryValues() {
        Movie minimum = Movie.create(
                "최솟값 영화",
                Movie.RUNTIME_MIN,
                Movie.RELEASE_YEAR_MIN,
                "movie/minimum/index.m3u8",
                null,
                AgeRating.ALL
        );
        Movie maximumRuntime = Movie.create(
                "최대 상영 시간 영화",
                Movie.RUNTIME_MAX,
                Movie.RELEASE_YEAR_MIN,
                "movie/maximum/index.m3u8",
                null,
                AgeRating.ALL
        );

        movieRepository.saveAllAndFlush(List.of(minimum, maximumRuntime));

        assertThat(minimum.getId()).isNotNull();
        assertThat(maximumRuntime.getId()).isNotNull();
    }

    private AggregateFixture saveAggregateFixture() {
        Person person = Person.create(
                "제약 검증 인물",
                null,
                null,
                null,
                null,
                List.of(),
                List.of("제약태그")
        );
        String galleryImageKey = "gallery/constraint/image.jpg";
        person.addGalleryImageKey(galleryImageKey);
        StoryboardProject project = person.addStoryboardProject("제약 프로젝트");
        StoryboardScene scene = project.addScene("제약 장면", null);
        StoryboardCard card = scene.addCard(null);
        personRepository.saveAndFlush(person);

        Movie movie = Movie.create(
                "제약 검증 영화",
                120,
                2026,
                "movie/constraint/index.m3u8",
                null,
                AgeRating.ALL
        );
        MoviePerson moviePerson = movie.addMoviePerson(
                person,
                PersonRole.ACTOR,
                CastType.LEAD,
                "주연"
        );
        Trailer trailer = movie.addTrailer("movie/constraint/trailer/index.m3u8");
        movieRepository.saveAndFlush(movie);

        return new AggregateFixture(
                movie.getId(),
                moviePerson.getId(),
                moviePerson.getRoles().get(0).getId(),
                trailer.getId(),
                person.getId(),
                person.getProfileTags().get(0).getId(),
                galleryImageKey,
                project.getId(),
                scene.getId(),
                card.getId()
        );
    }

    private static Stream<MovieRange> invalidMovieRanges() {
        return Stream.of(
                new MovieRange("runtime", Movie.RUNTIME_MIN - 1, "ck_movie_runtime"),
                new MovieRange("runtime", Movie.RUNTIME_MAX + 1, "ck_movie_runtime"),
                new MovieRange(
                        "release_year",
                        Movie.RELEASE_YEAR_MIN - 1,
                        "ck_movie_release_year_min"
                )
        );
    }

    private enum OrderTarget {
        MOVIE_PERSON(
                "movie_person",
                "id",
                "ck_movie_person_sort_order_non_negative",
                AggregateFixture::moviePersonId
        ),
        MOVIE_PERSON_ROLE(
                "movie_person_role",
                "id",
                "ck_movie_person_role_sort_order_non_negative",
                AggregateFixture::moviePersonRoleId
        ),
        TRAILER(
                "trailer",
                "id",
                "ck_trailer_sort_order_non_negative",
                AggregateFixture::trailerId
        ),
        PROFILE_TAG(
                "profile_tag",
                "id",
                "ck_profile_tag_sort_order_non_negative",
                AggregateFixture::profileTagId
        ),
        PERSON_GALLERY(
                "person_gallery",
                "person_id",
                "ck_person_gallery_sort_order_non_negative",
                AggregateFixture::personId
        ),
        STORYBOARD_PROJECT(
                "storyboard_project",
                "id",
                "ck_storyboard_project_sort_order_non_negative",
                AggregateFixture::projectId
        ),
        STORYBOARD_SCENE(
                "storyboard_scene",
                "id",
                "ck_storyboard_scene_sort_order_non_negative",
                AggregateFixture::sceneId
        ),
        STORYBOARD_CARD(
                "storyboard_card",
                "id",
                "ck_storyboard_card_sort_order_non_negative",
                AggregateFixture::cardId
        );

        private final String table;
        private final String idColumn;
        private final String constraintName;
        private final FixtureId fixtureId;

        OrderTarget(
                String table,
                String idColumn,
                String constraintName,
                FixtureId fixtureId
        ) {
            this.table = table;
            this.idColumn = idColumn;
            this.constraintName = constraintName;
            this.fixtureId = fixtureId;
        }

        private void updateSortOrder(
                JdbcTemplate jdbcTemplate,
                AggregateFixture fixture,
                int sortOrder
        ) {
            jdbcTemplate.update(
                    "update " + table + " set sort_order = ? where " + idColumn + " = ?",
                    sortOrder,
                    fixtureId.get(fixture)
            );
        }

        private int findSortOrder(JdbcTemplate jdbcTemplate, AggregateFixture fixture) {
            Integer value = jdbcTemplate.queryForObject(
                    "select sort_order from " + table + " where " + idColumn + " = ?",
                    Integer.class,
                    fixtureId.get(fixture)
            );
            return value == null ? -1 : value;
        }
    }

    @FunctionalInterface
    private interface FixtureId {
        Long get(AggregateFixture fixture);
    }

    private record AggregateFixture(
            Long movieId,
            Long moviePersonId,
            Long moviePersonRoleId,
            Long trailerId,
            Long personId,
            Long profileTagId,
            String galleryImageKey,
            Long projectId,
            Long sceneId,
            Long cardId
    ) {
    }

    private record MovieRange(String column, int value, String constraintName) {
    }
}
