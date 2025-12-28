package com.onfilm.domain.person.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.common.config.JwtProvider;
import com.onfilm.domain.movie.controller.PersonController;
import com.onfilm.domain.movie.dto.*;
import com.onfilm.domain.movie.entity.SnsType;
import com.onfilm.domain.movie.service.PersonService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(PersonController.class)
@AutoConfigureMockMvc(addFilters = false)
class PersonControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean
    private PersonService personService;

    @MockBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("POST /persons - 생성 성공 시 201과 personId를 반환한다")
    void createPerson_success() throws Exception {
        // given
        given(personService.createPerson(any(CreatePersonRequest.class)))
                .willReturn(1L);

        CreatePersonRequest request = new CreatePersonRequest(
                "레오나르도 디카프리오",
                LocalDate.of(1974, 11, 11),
                "미국 캘리포니아주 로스앤젤레스",
                "아카데미 수상 배우",
                "https://cdn.example.com/profiles/leonardo.jpg",
                List.of(
                        new CreatePersonSnsRequest(SnsType.INSTAGRAM, "https://www.instagram.com/leonardodicaprio/"),
                        new CreatePersonSnsRequest(SnsType.TWITTER, "https://x.com/leodicaprio")
                ),
                List.of("배우", "헐리우드", "환경운동")
        );

        // when & then
        mockMvc.perform(
                        post("/persons")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                // ResponseEntity<Long> 이면 body는 "1" 같은 plain number 문자열이야
                .andExpect(content().string("1"));
    }

    @Test
    @DisplayName("POST /persons - snsList가 null이어도 201을 반환한다")
    void createPerson_success_whenSnsListNull() throws Exception {
        // given
        given(personService.createPerson(any(CreatePersonRequest.class)))
                .willReturn(2L);

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
                        post("/persons")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(content().string("2"));
    }

    @Test
    @DisplayName("GET /persons/{name} -> 202 ACCEPTED + PersonResponse JSON 반환")
    void getPerson_returnsAcceptedAndBody() throws Exception {
        // given
        String name = "디카프리오";

        PersonSnsResponse sns1 = PersonSnsResponse.builder()
                        .type(SnsType.INSTAGRAM)
                        .url("https://instagram.com/leo")
                        .build();

        ProfileTagResponse tag1 = ProfileTagResponse.builder()
                        .rawTag("헐리우드")
                        .build();

        PersonResponse response = PersonResponse.builder()
                .id(1L)
                .name(name)
                .birthDate(LocalDate.of(1974, 11, 11))
                .birthPlace("Los Angeles")
                .oneLineIntro("actor")
                .profileImageUrl("https://img.test/profile.png")
                .snsList(List.of(sns1))
                .rawTags(List.of(tag1))
                .build();

        when(personService.getPerson(name)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/persons/{name}", name)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.birthDate").value("1974-11-11"))
                .andExpect(jsonPath("$.birthPlace").value("Los Angeles"))
                .andExpect(jsonPath("$.oneLineIntro").value("actor"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://img.test/profile.png"))
                .andExpect(jsonPath("$.snsList[0].type").value("INSTAGRAM"))
                .andExpect(jsonPath("$.snsList[0].url").value("https://instagram.com/leo"))
                .andExpect(jsonPath("$.rawTags[0].rawTag").value("헐리우드"));
    }
}
