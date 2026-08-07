package com.lifesync.api.task.controller;

import com.lifesync.api.common.ApiErrorResponse;
import com.lifesync.api.config.OpenApiConfig;
import com.lifesync.api.security.SecurityUser;
import com.lifesync.api.task.dto.*;
import com.lifesync.api.task.enums.Priority;
import com.lifesync.api.task.enums.Status;
import com.lifesync.api.task.service.TaskService;
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

import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

/**
 * Endpoints de Task e SubTask. Nenhum metodo recebe userId como parametro
 * do cliente - todos extraem via {@code @AuthenticationPrincipal}, direto
 * do SecurityUser que o JwtAuthFilter ja colocou no SecurityContext. Toda
 * logica de posse (IDOR) fica no TaskService, nao aqui.
 *
 * 401 nao e documentado endpoint a endpoint aqui de proposito - ver
 * Javadoc do UserController pra explicacao completa: ja fica implicito
 * pelo {@code @SecurityRequirement} da classe.
 */
@Tag(name = "Task", description = "Endpoints de Tarefas")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "Cria uma task para o usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task criada"),
            @ApiResponse(responseCode = "400", description = "Dado inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<TaskResponseDTO> createTask(@Valid @RequestBody TaskRequestDTO request,
                                                      @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        return ResponseEntity.status(CREATED).body(
                taskService.createTask(request, userId));
    }

    @Operation(summary = "Busca uma task pelo id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task encontrada"),
            @ApiResponse(responseCode = "404", description = "Task não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponseDTO> getTaskById(@PathVariable UUID taskId,
                                                       @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        TaskResponseDTO response = taskService.getTaskById(taskId, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Pageable e injetado automaticamente pelo Spring a partir da query
     * string (?page=0&size=20&sort=dueDate), sem parsing manual. status
     * e priority sao opcionais - omitidos, listam tudo.
     */
    @Operation(summary = "Lista as tasks do usuário autenticado, com filtro opcional por status e priority")
    @ApiResponse(responseCode = "200", description = "Lista retornada (pode vir vazia)")
    @GetMapping
    public ResponseEntity<Page<TaskResponseDTO>> getAllTasks(
            @Parameter(description = "Filtra por status (opcional)") @RequestParam(required = false) Status status,
            @Parameter(description = "Filtra por priority (opcional)") @RequestParam(required = false) Priority priority,
            Pageable pageable,
            @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        Page<TaskResponseDTO> tasks = taskService.getAllTasks(userId, status, priority, pageable);

        return ResponseEntity.ok(tasks);

    }

    @Operation(summary = "Atualiza título, descrição, prioridade e prazo de uma task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task atualizada"),
            @ApiResponse(responseCode = "400", description = "Dado inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{taskId}")
    public ResponseEntity<TaskResponseDTO> updateTask(@Valid @RequestBody TaskRequestDTO request,
                                                      @PathVariable UUID taskId,
                                                      @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        return ResponseEntity.status(OK).body(
                taskService.updateTask(taskId, request, userId));
    }

    @Operation(summary = "Remove uma task (e suas subtasks, em cascata)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task removida"),
            @ApiResponse(responseCode = "404", description = "Task não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID taskId,
                                           @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        taskService.deleteTask(taskId, userId);
        return ResponseEntity.status(NO_CONTENT).build();
    }

    /**
     * Mudanca de status fica num PATCH dedicado, separado do PUT geral
     * de updateTask - ver TaskRequestDTO/UpdateTaskStatusRequestDTO.
     */
    @Operation(summary = "Atualiza só o status da task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status atualizado"),
            @ApiResponse(responseCode = "400", description = "Dado inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping ("/{taskId}/status")
    public ResponseEntity<TaskResponseDTO> updateTaskStatus(@Valid @RequestBody UpdateTaskStatusRequestDTO request,
                                                            @PathVariable UUID taskId,
                                                            @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        TaskResponseDTO updatedTask = taskService.updateTaskStatus(taskId, userId, request);
        return ResponseEntity.ok(updatedTask);
    }

    @Operation(summary = "Adiciona uma subtask a uma task")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subtask criada"),
            @ApiResponse(responseCode = "400", description = "Dado inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task não existe ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{taskId}/subtasks")
    public ResponseEntity<SubTaskResponseDTO> addSubtask(@Valid @RequestBody SubTaskRequestDTO request,
                                                         @PathVariable UUID taskId,
                                                         @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        return ResponseEntity.status(CREATED).body(
                taskService.addSubtask(taskId, request, userId)
        );
    }

    @Operation(summary = "Atualiza o título de uma subtask")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subtask atualizada"),
            @ApiResponse(responseCode = "400", description = "Dado inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task ou subtask não existe, ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{taskId}/subtasks/{subtaskId}")
    public ResponseEntity<SubTaskResponseDTO> updateSubtask(@Valid @RequestBody SubTaskRequestDTO request,
                                                            @PathVariable UUID taskId,
                                                             @PathVariable UUID subtaskId,
                                                             @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        return ResponseEntity.status(OK).body(
                taskService.updateSubtask(taskId, subtaskId, request, userId)
        );
    }

    /**
     * Alterna o estado de conclusao da subtask (true vira false, e
     * vice-versa). Sem corpo de requisicao - o proprio estado atual e
     * quem decide o novo valor.
     */
    @Operation(summary = "Alterna a conclusão de uma subtask (feito/não feito)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado alternado"),
            @ApiResponse(responseCode = "404", description = "Task ou subtask não existe, ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{taskId}/subtasks/{subtaskId}")
    public ResponseEntity<SubTaskResponseDTO> toggleSubtaskCompletion(@PathVariable UUID taskId,
                                                                      @PathVariable UUID subtaskId,
                                                                      @AuthenticationPrincipal
                                                                          SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        SubTaskResponseDTO toggleTask = taskService.toggleSubtaskCompletion(taskId, subtaskId, userId);
        return ResponseEntity.ok(toggleTask);
    }

    @Operation(summary = "Remove uma subtask")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Subtask removida"),
            @ApiResponse(responseCode = "404", description = "Task ou subtask não existe, ou não pertence ao usuário autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{taskId}/subtasks/{subtaskId}")
    public ResponseEntity<Void> deleteSubtask(@PathVariable UUID taskId,
                                              @PathVariable UUID subtaskId,
                                              @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        taskService.deleteSubtask(taskId, subtaskId, userId);
        return ResponseEntity.status(NO_CONTENT).build();
    }
}
