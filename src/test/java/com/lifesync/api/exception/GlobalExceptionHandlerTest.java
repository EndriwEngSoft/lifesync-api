package com.lifesync.api.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste do GlobalExceptionHandler isolado (MockMvc standalone, sem subir o
 * contexto Spring): um controller de mentira lanca cada tipo de erro e o
 * teste confere o status HTTP e o formato da resposta. O ponto central e
 * garantir que erro do cliente (JSON quebrado, UUID invalido, rota
 * inexistente...) nao caia no catch-all e vire 500.
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void malformedJson_Returns400() throws Exception {
        mockMvc.perform(post("/fake/body").contentType(MediaType.APPLICATION_JSON).content("{nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body."));
    }

    /**
     * A mensagem da exception e do parser JSON e pode carregar trecho do corpo
     * enviado pelo cliente (dado do usuario); o log deve ter so o tipo.
     */
    @Test
    void malformedJson_LogsOnlyTheExceptionType(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/fake/body").contentType(MediaType.APPLICATION_JSON).content("{nao e json"));

        assertThat(output.getAll())
                .contains("Malformed request body: HttpMessageNotReadableException")
                .doesNotContain("JSON parse error");
    }

    @Test
    void validationError_IncludesFieldNameInMessage() throws Exception {
        mockMvc.perform(post("/fake/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("name: ")));
    }

    @Test
    void invalidUuidInPath_Returns400() throws Exception {
        mockMvc.perform(get("/fake/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'."));
    }

    @Test
    void missingRequiredHeader_Returns400() throws Exception {
        mockMvc.perform(get("/fake/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required request header or parameter is missing."));
    }

    @Test
    void unsupportedHttpMethod_Returns405() throws Exception {
        mockMvc.perform(delete("/fake/body"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unsupportedMediaType_Returns415() throws Exception {
        mockMvc.perform(post("/fake/body").contentType(MediaType.TEXT_PLAIN).content("texto"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unknownRoute_Returns404() throws Exception {
        mockMvc.perform(get("/fake/rota-que-nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found."));
    }

    @Test
    void dataIntegrityViolation_Returns409WithoutLeakingDatabaseMessage() throws Exception {
        mockMvc.perform(get("/fake/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data."))
                .andExpect(content().string(not(containsString("secret@mail.com"))));
    }

    @Test
    void unexpectedException_Returns500WithGenericMessage() throws Exception {
        mockMvc.perform(get("/fake/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(content().string(not(containsString("detalhe interno"))));
    }

    record FakePayload(@NotBlank String name) {
    }

    @RestController
    @RequestMapping("/fake")
    static class FakeController {

        @PostMapping("/body")
        String body(@Valid @RequestBody FakePayload payload) {
            return "ok";
        }

        @GetMapping("/items/{id}")
        String item(@PathVariable UUID id) {
            return "ok";
        }

        @GetMapping("/header")
        String header(@RequestHeader("X-Required") String value) {
            return "ok";
        }

        @GetMapping("/conflict")
        String conflict() {
            throw new DataIntegrityViolationException("duplicate key value (secret@mail.com)");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("detalhe interno");
        }
    }
}
