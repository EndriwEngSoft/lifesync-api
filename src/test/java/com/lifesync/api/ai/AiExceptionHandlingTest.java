package com.lifesync.api.ai;

import com.lifesync.api.ai.exception.AiDisabledException;
import com.lifesync.api.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mapeamento das excecoes do modulo de IA para o formato ApiErrorResponse,
 * com um controller de mentira (mesmo padrao do GlobalExceptionHandlerTest).
 */
class AiExceptionHandlingTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void aiDisabled_Returns503WithFixedMessage() throws Exception {
        mockMvc.perform(get("/fake-ai/disabled"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("AI feature is currently unavailable."))
                .andExpect(jsonPath("$.path").value("/fake-ai/disabled"));
    }

    @RestController
    @RequestMapping("/fake-ai")
    static class ThrowingController {

        @GetMapping("/disabled")
        String disabled() {
            throw new AiDisabledException("AI module is disabled.");
        }
    }
}
