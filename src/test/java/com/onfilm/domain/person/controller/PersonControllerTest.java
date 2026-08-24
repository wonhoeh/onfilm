package com.onfilm.domain.person.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.auth.security.JwtProvider;
import com.onfilm.domain.common.error.SecurityErrorResponseWriter;
import com.onfilm.domain.movie.controller.PersonController;
import com.onfilm.domain.movie.dto.*;
import com.onfilm.domain.movie.entity.SnsType;
import com.onfilm.domain.movie.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(PersonController.class)
@AutoConfigureMockMvc(addFilters = false)
class PersonControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean
    private PersonCommandService personCommandService;

    @MockBean
    private PersonQueryService personQueryService;

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private AuthProperties authProperties;

    @MockBean
    private SecurityErrorResponseWriter securityErrorResponseWriter;

    @MockBean
    private FilmographyQueryService filmographyQueryService;

    @MockBean
    private GalleryQueryService galleryQueryService;

    @MockBean
    private GalleryCommandService galleryCommandService;

    @MockBean
    private FilmographyCommandService filmographyCommandService;

    @MockBean
    private PersonMediaService personMediaService;

    @MockBean
    private StoryboardResponseMapper storyboardResponseMapper;

    @MockBean
    private StoryboardQueryService storyboardQueryService;

    @MockBean
    private StoryboardCommandService storyboardCommandService;

    @Test
    @DisplayName("POST /persons - 생성 성공 시 201과 personId를 반환한다")
    void createPerson_success() throws Exception {
        // given
        given(personCommandService.initializeProfile(any(CreatePersonRequest.class)))
                .willReturn(1L);

        CreatePersonRequest request = new CreatePersonRequest(
                "레오나르도 디카프리오",
                LocalDate.of(1974, 11, 11),
                "미국 캘리포니아주 로스앤젤레스",
                "아카데미 수상 배우",
                "https://cdn.example.com/profiles/leonardo.jpg",
                List.of(
                        new CreatePersonSnsRequest(SnsType.INSTAGRAM, "https://www.instagram.com/leonardodicaprio/"),
                        new CreatePersonSnsRequest(SnsType.TIKTOK, "https://tiktok.com/@leodicaprio")
                ),
                List.of("배우", "헐리우드", "환경운동")
        );

        // when & then
        mockMvc.perform(
                        post("/api/people")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                // ResponseEntity<Long> 이면 body는 "1" 같은 plain number 문자열이야
                .andExpect(content().string("1"));
    }

    @Test
    @DisplayName("POST /persons - snsList가 null이면 422를 반환한다")
    void createPerson_rejectsNullSnsList() throws Exception {
        CreatePersonRequest request = new CreatePersonRequest(
                "테스트 인물",
                LocalDate.of(2000, 1, 1),
                "서울",
                "한 줄 소개",
                null,
                null, // snsList null
                List.of("tag1", "tag2")
        );

        // when & then
        mockMvc.perform(
                        post("/api/people")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("snsList"));

        verifyNoInteractions(personCommandService);
    }

    @Test
    @DisplayName("POST /persons - SNS 타입과 URL이 유효하지 않으면 422를 반환한다")
    void createPerson_rejectsInvalidSnsRequest() throws Exception {
        CreatePersonRequest request = new CreatePersonRequest(
                "테스트 인물",
                LocalDate.of(2000, 1, 1),
                "서울",
                "한 줄 소개",
                null,
                List.of(new CreatePersonSnsRequest(null, " ")),
                List.of()
        );

        mockMvc.perform(
                        post("/api/people")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));

        verifyNoInteractions(personCommandService);
    }

    @Test
    @DisplayName("POST /persons - 프로필 태그가 20개를 넘으면 422를 반환한다")
    void createPerson_rejectsMoreThanTwentyProfileTags() throws Exception {
        CreatePersonRequest request = new CreatePersonRequest(
                "테스트 인물",
                LocalDate.of(2000, 1, 1),
                "서울",
                "한 줄 소개",
                null,
                List.of(),
                java.util.stream.IntStream.range(0, 21)
                        .mapToObj(i -> "tag-" + i)
                        .toList()
        );

        mockMvc.perform(
                        post("/api/people")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("rawTags"));

        verifyNoInteractions(personCommandService);
    }

    @Test
    @DisplayName("GET /api/people/{publicId} -> 200 OK + ProfileResponse JSON 반환")
    void getPerson_returnsOkAndBody() throws Exception {
        // given
        String name = "디카프리오";

        PersonSnsResponse sns1 = new PersonSnsResponse(
                SnsType.INSTAGRAM,
                "https://instagram.com/leo"
        );

        ProfileTagResponse tag1 = new ProfileTagResponse("헐리우드");

        String publicId = UUID.randomUUID().toString();
        ProfileResponse response = new ProfileResponse(
                publicId,
                name,
                LocalDate.of(1974, 11, 11),
                "Los Angeles",
                "actor",
                "profile/key.png",
                "https://img.test/profile.png",
                false,
                false,
                List.of(sns1),
                List.of(tag1)
        );

        when(personQueryService.findProfileByPublicId(publicId)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/people/{publicId}", publicId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.publicId").value(publicId))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.birthDate").value("1974-11-11"))
                .andExpect(jsonPath("$.birthPlace").value("Los Angeles"))
                .andExpect(jsonPath("$.oneLineIntro").value("actor"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://img.test/profile.png"))
                .andExpect(jsonPath("$.snsList[0].type").value("INSTAGRAM"))
                .andExpect(jsonPath("$.snsList[0].url").value("https://instagram.com/leo"))
                .andExpect(jsonPath("$.rawTags[0].rawTag").value("헐리우드"));
    }

    @Test
    @DisplayName("POST /storyboard/projects - 제목이 공백이면 422를 반환한다")
    void createStoryboardProject_rejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/people/{publicId}/storyboard/projects", "public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        verifyNoInteractions(storyboardCommandService);
    }

    @Test
    @DisplayName("PUT /storyboard/projects/{id}/scenes/order - sceneIds가 null이면 422를 반환한다")
    void reorderStoryboardScenes_rejectsNullSceneIds() throws Exception {
        mockMvc.perform(put("/api/people/{publicId}/storyboard/projects/{projectId}/scenes/order",
                        "public-id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneIds\":null}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("sceneIds"));

        verifyNoInteractions(storyboardCommandService);
    }

    @Test
    @DisplayName("POST /storyboard/projects/{id}/scenes - 제목이 120자를 넘으면 422를 반환한다")
    void createStoryboardScene_rejectsLongTitle() throws Exception {
        StoryboardSceneRequest request = new StoryboardSceneRequest(
                "a".repeat(121),
                null,
                List.of()
        );

        mockMvc.perform(post("/api/people/{publicId}/storyboard/projects/{projectId}/scenes",
                        "public-id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        verifyNoInteractions(storyboardCommandService);
    }

    @Test
    @DisplayName("PUT /storyboard/projects/{id}/scenes/{id} - cards가 null이면 422를 반환한다")
    void updateStoryboardScene_rejectsNullCards() throws Exception {
        StoryboardSceneRequest request = new StoryboardSceneRequest(
                "씬",
                null,
                null
        );

        mockMvc.perform(put("/api/people/{publicId}/storyboard/projects/{projectId}/scenes/{sceneId}",
                        "public-id", 1L, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("cards"));

        verifyNoInteractions(storyboardCommandService);
    }

    @Test
    @DisplayName("POST /storyboard/projects/{id}/scenes - 이미지 키가 512자를 넘으면 422를 반환한다")
    void createStoryboardScene_rejectsLongImageKey() throws Exception {
        StoryboardSceneRequest request = new StoryboardSceneRequest(
                "씬",
                null,
                List.of(new StoryboardCardRequest(null, "a".repeat(513)))
        );

        mockMvc.perform(post("/api/people/{publicId}/storyboard/projects/{projectId}/scenes",
                        "public-id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("cards[0].imageKey"));

        verifyNoInteractions(storyboardCommandService);
    }
}
