package com.lifesync.api.habit.controller;

import com.lifesync.api.common.ApiErrorResponse;
import com.lifesync.api.config.OpenApiConfig;
import com.lifesync.api.habit.dto.HabitHistoryResponseDTO;
import com.lifesync.api.habit.dto.HabitRequestDTO;
import com.lifesync.api.habit.dto.HabitResponseDTO;
import com.lifesync.api.habit.service.HabitService;
import com.lifesync.api.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

/**
 * Endpoints de Habito e historico de check-ins. Toda logica de negocio
 * (streak, soft delete) fica no HabitService, nao aqui.
 *
 * 401 nao e documentado endpoint a endpoint aqui de proposito - ver
 * Javadoc do UserController pra explicacao completa: ja fica implicito
 * pelo {@code @SecurityRequirement} da classe.
 */
@Tag(name = "Habit", description = "Endpoints de Habitos")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
@RestController
@RequestMapping("/api/habits")
@RequiredArgsConstructor
public class HabitController {

    private final HabitService habitService;

    @Operation(summary = "Cria um hábito para o usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Hábito criado"),
            @ApiResponse(responseCode = "400", description = "Dado inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<HabitResponseDTO> createHabit(@Valid @RequestBody HabitRequestDTO request,
                                                        @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.status(CREATED).body(habitService.createHabit(request, userId));
    }

    @Operation(summary = "Busca um hábito pelo id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hábito encontrado"),
            @ApiResponse(responseCode = "404", description = "Hábito não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{habitId}")
    public ResponseEntity<HabitResponseDTO> getHabitById(@PathVariable UUID habitId,
                                                         @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(habitService.getHabitById(habitId, userId));
    }

    @Operation(summary = "Lista os hábitos do usuário autenticado")
    @ApiResponse(responseCode = "200", description = "Lista retornada (pode vir vazia)")
    @GetMapping
    public ResponseEntity<Page<HabitResponseDTO>> getAllHabits(Pageable pageable,
                                                               @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(habitService.getAllHabits(userId, pageable));
    }

    @Operation(summary = "Atualiza um hábito (nome, descrição, frequência, meta)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hábito atualizado"),
            @ApiResponse(responseCode = "400", description = "Dado inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Hábito não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{habitId}")
    public ResponseEntity<HabitResponseDTO> updateHabit(@Valid @RequestBody HabitRequestDTO request,
                                                        @PathVariable UUID habitId,
                                                        @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(habitService.updateHabit(habitId, request, userId));
    }

    @Operation(summary = "Remove um hábito (soft delete)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Hábito removido"),
            @ApiResponse(responseCode = "404", description = "Hábito não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{habitId}")
    public ResponseEntity<Void> deleteHabit(@PathVariable UUID habitId,
                                            @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        habitService.deleteHabit(habitId, userId);
        return ResponseEntity.status(NO_CONTENT).build();
    }

    @Operation(summary = "Marca check-in para o hábito (cria um registro de histórico)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Check-in registrado"),
            @ApiResponse(responseCode = "409", description = "Check-in já registrado para hoje",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{habitId}/checkin")
    public ResponseEntity<HabitResponseDTO> checkIn(@PathVariable UUID habitId,
                                                    @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.status(CREATED).body(habitService.checkIn(habitId, userId));
    }

    @Operation(summary = "Histórico de check-ins de um hábito")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico retornado (pode vir vazio)"),
            @ApiResponse(responseCode = "404", description = "Hábito não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{habitId}/history")
    public ResponseEntity<Page<HabitHistoryResponseDTO>> getHistory(@PathVariable UUID habitId,
                                                                     Pageable pageable,
                                                                     @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(habitService.getHistory(habitId, userId, pageable));
    }
}
