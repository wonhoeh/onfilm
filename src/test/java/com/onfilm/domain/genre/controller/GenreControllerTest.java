package com.onfilm.domain.genre.controller;

import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.auth.security.JwtProvider;
import com.onfilm.domain.common.error.SecurityErrorResponseWriter;
import com.onfilm.domain.genre.dto.GenreAutocompleteResponse;
import com.onfilm.domain.genre.service.GenreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GenreController.class)
@AutoConfigureMockMvc(addFilters = false)
class GenreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GenreService genreService;

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private AuthProperties authProperties;

    @MockBean
    private SecurityErrorResponseWriter securityErrorResponseWriter;

    @Test
    void autocomplete_returnsStandardGenres() throws Exception {
        given(genreService.autocomplete("act"))
                .willReturn(List.of(
                        new GenreAutocompleteResponse(1L, "Action"),
                        new GenreAutocompleteResponse(2L, "Action Comedy")
                ));

        mockMvc.perform(get("/api/genres/autocomplete")
                        .param("query", "act"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("Action"))
                .andExpect(jsonPath("$[1].id").value(2L))
                .andExpect(jsonPath("$[1].name").value("Action Comedy"));
    }
}
