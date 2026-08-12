package com.lifesync.api.goal.controller;

import com.lifesync.api.common.ApiErrorResponse;
import com.lifesync.api.config.OpenApiConfig;
import com.lifesync.api.goal.dto.GoalProgressRequestDTO;
import com.lifesync.api.goal.dto.GoalProgressResponseDTO;
import com.lifesync.api.goal.dto.GoalRequestDTO;
import com.lifesync.api.goal.dto.GoalResponseDTO;
import com.lifesync.api.goal.enums.GoalStatus;
import com.lifesync.api.goal.service.GoalService;
import com.lifesync.api.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

/**
 * Endpoints de Meta e registro de progresso. Toda logica de negocio
 * (calculo de percentual, transicao pra COMPLETED, historico) fica no
 * GoalService, nao aqui.
 *
 * 401 nao e documentado endpoint a endpoint aqui de proposito - ver
 * Javadoc do UserController pra explicacao completa: ja fica implicito
 * pelo {@code @SecurityRequirement} da classe.
 */
@Tag(name = "Goal", description = "Endpoints de Metas")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @Operation(summary = "Cria uma meta para o usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Meta criada"),
            @ApiResponse(responseCode = "400", description = "Dado inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<GoalResponseDTO> createGoal(@Valid @RequestBody GoalRequestDTO request,
                                                        @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.status(CREATED).body(goalService.createGoal(request, userId));
    }

    @Operation(summary = "Busca uma meta pelo id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Meta encontrada"),
            @ApiResponse(responseCode = "404", description = "Meta não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{goalId}")
    public ResponseEntity<GoalResponseDTO> getGoalById(@PathVariable UUID goalId,
                                                        @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(goalService.getGoalById(goalId, userId));
    }

    @Operation(summary = "Lista as metas do usuário autenticado, com filtro opcional por status")
    @ApiResponse(responseCode = "200", description = "Lista retornada (pode vir vazia)")
    @GetMapping
    public ResponseEntity<Page<GoalResponseDTO>> getAllGoals(@Parameter(description = "Filtro opcional por status")
                                                               @RequestParam(required = false) GoalStatus status,
                                                               Pageable pageable,
                                                               @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(goalService.getAllGoals(userId, status, pageable));
    }

    @Operation(summary = "Atualiza uma meta (título, descrição, valor alvo, unidade, prazo)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Meta atualizada"),
            @ApiResponse(responseCode = "400", description = "Dado inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Meta não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{goalId}")
    public ResponseEntity<GoalResponseDTO> updateGoal(@Valid @RequestBody GoalRequestDTO request,
                                                        @PathVariable UUID goalId,
                                                        @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(goalService.updateGoal(goalId, request, userId));
    }

    @Operation(summary = "Remove uma meta (desvincula tasks/hábitos, apaga o histórico de progresso)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Meta removida"),
            @ApiResponse(responseCode = "404", description = "Meta não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{goalId}")
    public ResponseEntity<Void> deleteGoal(@PathVariable UUID goalId,
                                            @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        goalService.deleteGoal(goalId, userId);
        return ResponseEntity.status(NO_CONTENT).build();
    }

    @Operation(summary = "Registra um novo valor de progresso para a meta")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progresso registrado, meta atualizada"),
            @ApiResponse(responseCode = "400", description = "Dado inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Meta não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{goalId}/progress")
    public ResponseEntity<GoalResponseDTO> recordProgress(@PathVariable UUID goalId,
                                                            @Valid @RequestBody GoalProgressRequestDTO request,
                                                            @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(goalService.recordProgress(goalId, userId, request));
    }

    @Operation(summary = "Histórico de progresso de uma meta")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico retornado (pode vir vazio)"),
            @ApiResponse(responseCode = "404", description = "Meta não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{goalId}/progress")
    public ResponseEntity<List<GoalProgressResponseDTO>> getProgressHistory(@PathVariable UUID goalId,
                                                                              @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(goalService.getProgressHistory(goalId, userId));
    }

}
