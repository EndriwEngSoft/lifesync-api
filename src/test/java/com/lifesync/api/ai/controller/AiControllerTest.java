package com.lifesync.api.ai.controller;

import com.lifesync.api.ai.dto.WeeklyPlanResponseDTO;
import com.lifesync.api.ai.dto.WeeklyPlanResponseDTO.PrioritizedItemDTO;
import com.lifesync.api.ai.enums.ReferenceType;
import com.lifesync.api.ai.exception.AiQuotaExceededException;
import com.lifesync.api.ai.service.WeeklyPlanService;
import com.lifesync.api.support.ApiTestSupport;
import com.lifesync.api.support.PostgresTestConfig;
import com.lifesync.api.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller com a cadeia de seguranca real (filtro JWT) e o servico
 * mockado: prova autenticacao e que o userId vem sempre do token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
@Transactional
class AiControllerTest extends ApiTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeeklyPlanService weeklyPlanService;

    @Test
    void withoutToken_Returns401AndDoesNotCallTheService() throws Exception {
        mockMvc.perform(post("/api/ai/weekly-plan"))
                .andExpect(status().isUnauthorized());

        verify(weeklyPlanService, never()).generate(any());
    }

    @Test
    void withGarbageToken_Returns401() throws Exception {
        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", "Bearer isto.nao.e.jwt"))
                .andExpect(status().isUnauthorized());

        verify(weeklyPlanService, never()).generate(any());
    }

    @Test
    void withValidToken_Returns200WithThePlanWithRealIds() throws Exception {
        User user = newUser("a");
        UUID itemId = UUID.randomUUID();
        Map<DayOfWeek, List<String>> schedule = new EnumMap<>(DayOfWeek.class);
        schedule.put(DayOfWeek.MONDAY, List.of("Comecar cedo."));
        when(weeklyPlanService.generate(user.getId())).thenReturn(new WeeklyPlanResponseDTO("Resumo",
                List.of(new PrioritizedItemDTO(ReferenceType.TASK, itemId, "Vence amanha.")),
                List.of("Risco 1"), schedule));

        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Resumo"))
                .andExpect(jsonPath("$.priorities[0].type").value("TASK"))
                .andExpect(jsonPath("$.priorities[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.priorities[0].reason").value("Vence amanha."))
                .andExpect(jsonPath("$.risks[0]").value("Risco 1"))
                .andExpect(jsonPath("$.schedule.MONDAY[0]").value("Comecar cedo."));
    }

    @Test
    void userIdComesFromTheToken_NotFromTheRequest() throws Exception {
        User userA = newUser("a");
        User userB = newUser("b");
        when(weeklyPlanService.generate(any())).thenReturn(
                new WeeklyPlanResponseDTO("ok", List.of(), List.of(), Map.of()));

        // O cliente tenta se passar pelo B no corpo e na query; o token e do A
        mockMvc.perform(post("/api/ai/weekly-plan?userId=" + userB.getId())
                        .header("Authorization", bearer(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userB.getId() + "\"}"))
                .andExpect(status().isOk());

        verify(weeklyPlanService).generate(userA.getId());
        verify(weeklyPlanService, never()).generate(userB.getId());
    }

    @Test
    void serviceErrors_AreReturnedInTheStandardFormat() throws Exception {
        User user = newUser("a");
        when(weeklyPlanService.generate(user.getId()))
                .thenThrow(new AiQuotaExceededException("Daily AI quota reached. Try again tomorrow."));

        mockMvc.perform(post("/api/ai/weekly-plan").header("Authorization", bearer(user)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/api/ai/weekly-plan"));
    }

    @Test
    void openApiDocumentsTheEndpointAndItsErrorResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/ai/weekly-plan'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/ai/weekly-plan'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/ai/weekly-plan'].post.responses['429']").exists())
                .andExpect(jsonPath("$.paths['/api/ai/weekly-plan'].post.responses['502']").exists())
                .andExpect(jsonPath("$.paths['/api/ai/weekly-plan'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/ai/weekly-plan'].post.responses['504']").exists())
                .andExpect(jsonPath("$.paths['/api/ai/weekly-plan'].post.security").exists());
    }

    @Test
    void onlyPostIsAllowed() throws Exception {
        User user = newUser("a");

        mockMvc.perform(get("/api/ai/weekly-plan").header("Authorization", bearer(user)))
                .andExpect(status().isMethodNotAllowed());
    }
}
