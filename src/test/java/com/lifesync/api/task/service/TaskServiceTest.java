package com.lifesync.api.task.service;

import com.lifesync.api.exception.ResourceNotFoundException;
import com.lifesync.api.task.dto.UpdateTaskStatusRequestDTO;
import com.lifesync.api.task.entity.SubTask;
import com.lifesync.api.task.entity.Task;
import com.lifesync.api.task.enums.Status;
import com.lifesync.api.task.repository.SubTaskRepository;
import com.lifesync.api.task.repository.TaskRepository;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Teste unitario com mocks (Mockito) - TaskService depende de
 * TaskRepository/SubTaskRepository/UserService, entao aqui a gente
 * substitui os tres por dublês controlados (@Mock) em vez de subir banco
 * real. @InjectMocks monta o TaskService injetando os mocks acima nele.
 * Foco nos dois pontos de maior risco: a regra de completedAt derivado
 * do status, e a defesa contra IDOR (Task/SubTask nao encontrados
 * viram ResourceNotFoundException, nunca vazam dado de outro usuario).
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private SubTaskRepository subTaskRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private TaskService taskService;

    private UUID userId;
    private UUID taskId;
    private Task fakeTask;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        taskId = UUID.randomUUID();

        User fakeUser = new User();
        fakeUser.setId(userId);
        fakeUser.setName("Usuário Teste");

        fakeTask = new Task();
        fakeTask.setId(taskId);
        fakeTask.setStatus(Status.PENDING);

        fakeTask.setUser(fakeUser);

        fakeTask.setSubTasks(new java.util.ArrayList<>());
    }

    @Test
    void updateTaskStatus_ToDone_SetsCompletedAt() {
        UpdateTaskStatusRequestDTO dto = new UpdateTaskStatusRequestDTO();
        dto.setStatus(Status.DONE);

        when(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.of(fakeTask));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        taskService.updateTaskStatus(taskId, userId, dto);

        assertEquals(Status.DONE, fakeTask.getStatus(), "Status should change to DONE");
        assertNotNull(fakeTask.getCompletedAt(), "completedAt should be set when status is DONE");
    }

    @Test
    void updateTaskStatus_ToInProgress_ClearsCompletedAt() {
        UpdateTaskStatusRequestDTO dto = new UpdateTaskStatusRequestDTO();
        fakeTask.setStatus(Status.DONE);
        fakeTask.setCompletedAt(Instant.now());
        dto.setStatus(Status.IN_PROGRESS);

        when(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.of(fakeTask));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        taskService.updateTaskStatus(taskId, userId, dto);

        assertEquals(Status.IN_PROGRESS, fakeTask.getStatus(), "Status should change to IN_PROGRESS");
        assertNull(fakeTask.getCompletedAt(), "completedAt should be cleared back to null when status is not DONE");
    }

    @Test
    void updateTaskStatus_WithWrongUserId_ThrowsResourceNotFoundException() {
        UpdateTaskStatusRequestDTO dto = new UpdateTaskStatusRequestDTO();
        dto.setStatus(Status.DONE);

        when(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            taskService.updateTaskStatus(taskId, userId, dto));
        }

    @Test
    void toggleSubtaskCompletion_WithWrongSubtaskId_ThrowsException() {
        UUID subtaskId = UUID.randomUUID();

        when(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.of(fakeTask));

        when(subTaskRepository.findByIdAndTaskId(subtaskId, taskId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                taskService.toggleSubtaskCompletion(taskId, subtaskId, userId)
        );
    }
}