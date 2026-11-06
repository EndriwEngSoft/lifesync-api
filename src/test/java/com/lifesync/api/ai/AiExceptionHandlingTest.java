package com.lifesync.api.ai;

import com.lifesync.api.ai.exception.AiDisabledException;
import com.lifesync.api.ai.exception.AiQuotaExceededException;
import com.lifesync.api.ai.exception.InvalidLlmOutputException;
import com.lifesync.api.ai.exception.LlmProviderException;
import com.lifesync.api.ai.exception.LlmProviderException.Type;
import com.lifesync.api.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                .andExpect(jsonPath("$.path").value("/fake-ai/disabled"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void quotaExceeded_Returns429() throws Exception {
        mockMvc.perform(get("/fake-ai/quota"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Daily AI quota reached. Try again tomorrow."));
    }

    @Test
    void providerTimeout_Returns504() throws Exception {
        mockMvc.perform(get("/fake-ai/provider/TIMEOUT"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.message").value("The AI provider took too long to respond."));
    }

    @ParameterizedTest
    @EnumSource(value = Type.class, names = "TIMEOUT", mode = EnumSource.Mode.EXCLUDE)
    void otherProviderFailures_Return502WithoutLeakingDetails(Type type) throws Exception {
        mockMvc.perform(get("/fake-ai/provider/" + type.name()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("The AI provider is unavailable or returned an error."))
                .andExpect(content().string(not(containsString("detalhe-interno-do-provedor"))));
    }

    @Test
    void invalidModelOutput_Returns502WithoutTheModelText() throws Exception {
        mockMvc.perform(get("/fake-ai/invalid-output"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("The AI provider returned an invalid response."))
                .andExpect(content().string(not(containsString("texto-do-modelo"))));
    }

    @RestController
    @RequestMapping("/fake-ai")
    static class ThrowingController {

        @GetMapping("/disabled")
        String disabled() {
            throw new AiDisabledException("AI module is disabled.");
        }

        @GetMapping("/quota")
        String quota() {
            throw new AiQuotaExceededException("Daily AI quota reached. Try again tomorrow.");
        }

        @GetMapping("/provider/{type}")
        String provider(@PathVariable Type type) {
            throw new LlmProviderException(type, "detalhe-interno-do-provedor");
        }

        @GetMapping("/invalid-output")
        String invalidOutput() {
            throw new InvalidLlmOutputException("campos invalidos: texto-do-modelo");
        }
    }
}
