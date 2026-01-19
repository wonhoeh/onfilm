package com.onfilm.domain.person.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.movie.dto.CreateMovieRequest;
import com.onfilm.domain.movie.entity.*;
import com.onfilm.domain.movie.repository.MovieRepository;
import com.onfilm.domain.movie.service.MovieGenreNormalizer;
import com.onfilm.domain.movie.service.MovieService;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
public class MovieServiceTest {

    @Mock MovieRepository movieRepository;
    @Mock UserRepository userRepository;
    @Mock
    MovieGenreNormalizer movieGenreNormalizer;

    @InjectMocks
    MovieService movieService;

    @Test
    void createMovie_success_savesMovie_attachesGenre_and_createsMoviePerson() throws Exception {
        // given
        CreateMovieRequest request = mock(CreateMovieRequest.class);
        given(request.getTitle()).willReturn("인셉션");
        given(request.getRuntime()).willReturn(120);
        given(request.getReleaseYear()).willReturn(2010);
        given(request.getMovieUrl()).willReturn("s3://movie.mp4");
        given(request.getThumbnailUrl()).willReturn("s3://thumb.png");
        given(request.getTrailerUrls()).willReturn(List.of("https://trailer1", "https://trailer2"));
        given(request.getAgeRating()).willReturn(AgeRating.ALL);

        given(request.getRole()).willReturn(PersonRole.ACTOR);
        given(request.getCastType()).willReturn(CastType.LEAD);
        given(request.getCharacterName()).willReturn("코브");

        List<String> rawGenres = List.of("드라마", " 스릴러 ", "드라마"); // 중복 포함
        given(request.getRawGenreTexts()).willReturn(rawGenres);

        long userId = 1L;
        User user = mock(User.class);
        Person person = mock(Person.class);
        given(user.getPerson()).willReturn(person);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // save 시 id가 생긴 것처럼 만들어주기(단위테스트라 JPA가 없으니 reflection으로 세팅)
        given(movieRepository.save(any(Movie.class))).willAnswer(inv -> {
            Movie m = inv.getArgument(0);
            setId(m, 10L);
            return m;
        });

        // static mocking
        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(userId);

            ArgumentCaptor<Movie> movieCaptor = ArgumentCaptor.forClass(Movie.class);

            // when
            Long resultId = movieService.createMovie(request);

            // then
            assertThat(resultId).isEqualTo(10L);

            // 1) 장르 부착 호출 확인
            then(movieGenreNormalizer).should(times(1))
                    .attachGenre(movieCaptor.capture(), eq(rawGenres));

            // 2) 저장 호출 확인
            then(movieRepository).should(times(1)).save(any(Movie.class));

            // 3) MoviePerson이 Movie에 소속되었는지 확인(연관관계 캡슐화 검증)
            Movie createdMovie = movieCaptor.getValue();
            assertThat(createdMovie.getMoviePeople()).hasSize(1);

            MoviePerson mp = createdMovie.getMoviePeople().get(0);
            assertThat(mp.getPerson()).isSameAs(person);
            assertThat(mp.getRole()).isEqualTo(PersonRole.ACTOR);
            assertThat(mp.getCastType()).isEqualTo(CastType.LEAD);
            assertThat(mp.getCharacterName()).isEqualTo("코브");
        }
    }

    @Test
    void createMovie_throws_when_userNotFound() {
        // given
        CreateMovieRequest request = mock(CreateMovieRequest.class);

        // ✅ Movie.create() 통과용 최소 스텁
        given(request.getTitle()).willReturn("인셉션");
        given(request.getRuntime()).willReturn(120);
        given(request.getReleaseYear()).willReturn(2010);
        given(request.getMovieUrl()).willReturn("url");
        given(request.getThumbnailUrl()).willReturn(null);
        given(request.getTrailerUrls()).willReturn(List.of());
        given(request.getAgeRating()).willReturn(AgeRating.ALL);

        long userId = 1L;
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(userId);

            // when & then
            assertThatThrownBy(() -> movieService.createMovie(request))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Test
    void createMovie_throws_when_personIsNull() {
        // given
        CreateMovieRequest request = mock(CreateMovieRequest.class);
        given(request.getTitle()).willReturn("인셉션");
        given(request.getRuntime()).willReturn(120);
        given(request.getReleaseYear()).willReturn(2010);
        given(request.getMovieUrl()).willReturn("url");
        given(request.getThumbnailUrl()).willReturn(null);
        given(request.getTrailerUrls()).willReturn(List.of());
        given(request.getAgeRating()).willReturn(AgeRating.ALL);

        long userId = 1L;
        User user = mock(User.class);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(user.getPerson()).willReturn(null);

        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(userId);

            assertThatThrownBy(() -> movieService.createMovie(request))
                    .isInstanceOf(PersonNotFoundException.class);
        }
    }

    private static void setId(Movie movie, Long id) throws Exception {
        Field f = Movie.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(movie, id);
    }
}
