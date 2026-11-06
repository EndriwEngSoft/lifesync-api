package com.lifesync.api.ai.controller;

import com.lifesync.api.ai.dto.WeeklyPlanResponseDTO;
import com.lifesync.api.ai.service.WeeklyPlanService;
import com.lifesync.api.common.ApiErrorResponse;
import com.lifesync.api.config.OpenApiConfig;
import com.lifesync.api.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints de IA. O endpoint nao recebe corpo nem parametros: o plano e
 * sempre do usuario do token (userId vem do contexto de seguranca, nunca da
 * requisicao). Toda a logica fica no WeeklyPlanService.
 *
 * 401 nao e documentado endpoint a endpoint de proposito - ja fica implicito
 * pelo {@code @SecurityRequirement} da classe (ver Javadoc do UserController).
 */
@Tag(name = "AI", description = "Recursos de inteligência artificial")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final WeeklyPlanService weeklyPlanService;

    @Operation(
            summary = "Gera o plano semanal do usuário autenticado",
            description = "Lê as tarefas em aberto, os hábitos ativos e as metas em andamento do próprio usuário, "
                    + "envia um resumo minimizado (sem nome, e-mail nem ids) a um modelo de linguagem e devolve "
                    + "um plano semanal validado. Há uma cota diária por usuário. Sem dados cadastrados, "
                    + "devolve um plano vazio sem consultar o modelo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plano gerado (ou plano vazio, se não há dados)"),
            @ApiResponse(responseCode = "429", description = "Cota diária de IA esgotada",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "O provedor de IA falhou ou devolveu uma resposta inválida",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "O recurso de IA está desligado ou não configurado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "O provedor de IA demorou demais para responder",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/weekly-plan")
    public ResponseEntity<WeeklyPlanResponseDTO> generateWeeklyPlan(
            @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(weeklyPlanService.generate(userId));
    }
}
