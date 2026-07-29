package com.lifesync.api.task.controller;

import com.lifesync.api.security.SecurityUser;
import com.lifesync.api.task.dto.*;
import com.lifesync.api.task.service.TaskService;
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
 */
@Tag(name = "Task", description = "Endpoints de Tarefas")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResponseDTO> createTask(@Valid @RequestBody TaskRequestDTO request,
                                                      @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        return ResponseEntity.status(CREATED).body(
                taskService.createTask(request, userId));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponseDTO> getTaskById(@PathVariable UUID taskId,
                                                       @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        TaskResponseDTO response = taskService.getTaskById(taskId, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Pageable e injetado automaticamente pelo Spring a partir da query
     * string (?page=0&size=20&sort=dueDate), sem parsing manual.
     */
    @GetMapping
    public ResponseEntity<Page<TaskResponseDTO>> getAllTasks(Pageable pageable,
                                                              @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        Page<TaskResponseDTO> tasks = taskService.getAllTasks(userId, pageable);

        return ResponseEntity.ok(tasks);

    }

    @PutMapping("/{taskId}")
    public ResponseEntity<TaskResponseDTO> updateTask(@Valid @RequestBody TaskRequestDTO request,
                                                      @PathVariable UUID taskId,
                                                      @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        return ResponseEntity.status(OK).body(
                taskService.updateTask(taskId, request, userId));
    }

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
    @PatchMapping ("/{taskId}/status")
    public ResponseEntity<TaskResponseDTO> updateTaskStatus(@Valid @RequestBody UpdateTaskStatusRequestDTO request,
                                                            @PathVariable UUID taskId,
                                                            @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        TaskResponseDTO updatedTask = taskService.updateTaskStatus(taskId, userId, request);
        return ResponseEntity.ok(updatedTask);
    }

    @PostMapping("/{taskId}/subtasks")
    public ResponseEntity<SubTaskResponseDTO> addSubtask(@Valid @RequestBody SubTaskRequestDTO request,
                                                         @PathVariable UUID taskId,
                                                         @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        return ResponseEntity.status(CREATED).body(
                taskService.addSubtask(taskId, request, userId)
        );
    }

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
    @PatchMapping("/{taskId}/subtasks/{subtaskId}")
    public ResponseEntity<SubTaskResponseDTO> toggleSubtaskCompletion(@PathVariable UUID taskId,
                                                                      @PathVariable UUID subtaskId,
                                                                      @AuthenticationPrincipal
                                                                          SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        SubTaskResponseDTO toggleTask = taskService.toggleSubtaskCompletion(taskId, subtaskId, userId);
        return ResponseEntity.ok(toggleTask);
    }

    @DeleteMapping("/{taskId}/subtasks/{subtaskId}")
    public ResponseEntity<Void> deleteSubtask(@PathVariable UUID taskId,
                                              @PathVariable UUID subtaskId,
                                              @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();

        taskService.deleteSubtask(taskId, subtaskId, userId);
        return ResponseEntity.status(NO_CONTENT).build();
    }
}
