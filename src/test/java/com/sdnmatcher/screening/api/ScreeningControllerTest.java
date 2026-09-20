package com.sdnmatcher.screening.api;

import com.sdnmatcher.screening.service.AccountNotFoundException;
import com.sdnmatcher.screening.service.ScreeningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ScreeningControllerTest {
    private final ScreeningService service = mock(ScreeningService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new ScreeningController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getReturnsSingleScreeningAsJson() throws Exception {
        var match = new MatchResult("30962", Confidence.HIGH,
                List.of(MatchType.NAME_EXACT, MatchType.DOB_FULL));
        when(service.screenOne("1001")).thenReturn(new ScreeningResult("1001", List.of(match)));

        mvc.perform(get("/screenings/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").value("1001"))
                .andExpect(jsonPath("$.matches[0].uid").value("30962"))
                .andExpect(jsonPath("$.matches[0].confidence").value("HIGH"))
                .andExpect(jsonPath("$.matches[0].match_types[0]").value("NAME_EXACT"));
    }

    @Test
    void postReturnsBulkScreeningsAsJsonArray() throws Exception {
        when(service.screenAll()).thenReturn(List.of(new ScreeningResult("1001", List.of())));

        mvc.perform(post("/screenings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].account_id").value("1001"))
                .andExpect(jsonPath("$[0].matches").isEmpty());
    }

    @Test
    void unknownAccountReturns404ProblemDetail() throws Exception {
        when(service.screenOne("missing")).thenThrow(new AccountNotFoundException("missing"));

        mvc.perform(get("/screenings/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found: missing"));
    }
}
