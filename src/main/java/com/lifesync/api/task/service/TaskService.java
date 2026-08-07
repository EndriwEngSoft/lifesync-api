package com.lifesync.api.task.service;

import com.lifesync.api.exception.ResourceNotFoundException;
import com.lifesync.api.task.dto.SubTaskRequestDTO;
import com.lifesync.api.task.dto.SubTaskResponseDTO;
import com.lifesync.api.task.dto.TaskRequestDTO;
import com.lifesync.api.task.dto.TaskResponseDTO;
import com.lifesync.api.task.dto.UpdateTaskStatusRequestDTO;
import com.lifesync.api.task.entity.SubTask;
import com.lifesync.api.task.entity.Task;
import com.lifesync.api.task.enums.Priority;
import com.lifesync.api.task.enums.Status;
import com.lifesync.api.task.repository.SubTaskRepository;
import com.lifesync.api.task.repository.TaskRepository;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Toda consulta/alteracao de Task e SubTask e escopada pelo userId de quem
 * esta autenticado - nunca busca por id sozinho. E a defesa contra IDOR:
 * TaskRepository.findByIdAndUserId devolve vazio (nao a Task de outro
 * usuario) se o id nao pertencer a quem esta pedindo, e isso vira 404 via
 * ResourceNotFoundException, sem revelar que o recurso existe.
 */
@RequiredArgsConstructor
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final SubTaskRepository subTaskRepository;
    private final UserService userService;

    /**
     * Toda Task nasce com status PENDING - status nao e um campo livre
     * de criacao, muda so via updateTaskStatus (PATCH dedicado).
     */
    @Transactional
    public TaskResponseDTO createTask(TaskRequestDTO request, UUID userId) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Task task = new Task();
        task.setUser(user);
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setPriority(request.getPriority());
        task.setDueDate(request.getDueDate());
        task.setStatus(Status.PENDING);

        Task savedTask = taskRepository.save(task);
        return toResponseDTO(savedTask);
    }

    /**
     * findByIdAndUserId ja e a defesa contra IDOR: se a Task nao pertencer
     * a esse userId, a query devolve vazio e o orElseThrow dispara antes
     * de qualquer coisa. Nao precisa (e nao deveria) checar posse de novo
     * aqui dentro - isso ja aconteceu no nivel da query.
     */
    @Transactional(readOnly = true)
    public TaskResponseDTO getTaskById(UUID taskId, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        return toResponseDTO(task);
    }

    /**
     * Lista paginada das tarefas do usuario autenticado, com filtro
     * opcional por status e/ou priority - status e priority nulos
     * significam "sem filtro nessa dimensao", nao "nenhum resultado".
     * A logica de opcionalidade fica toda no
     * TaskRepository.findByUserIdWithFilters; o service so repassa.
     */
    @Transactional(readOnly = true)
    public Page<TaskResponseDTO> getAllTasks(UUID userId, Status status, Priority priority, Pageable pageable) {
        Page<Task> tasks = taskRepository.findByUserIdWithFilters(userId, status, priority, pageable);
        return tasks.map(this::toResponseDTO);
    }

    /**
     * Edicao geral (titulo, descricao, prioridade, prazo). Nao mexe em
     * status de proposito - ver updateTaskStatus.
     */
    @Transactional
    public TaskResponseDTO updateTask(UUID taskId, TaskRequestDTO dto, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setPriority(dto.getPriority());
        task.setDueDate(dto.getDueDate());

        Task savedTask = taskRepository.save(task);
        return toResponseDTO(savedTask);
    }

    /**
     * cascade = ALL + orphanRemoval em Task.subTasks ja cuida de apagar
     * as subtasks junto - nao precisa deletar elas manualmente aqui.
     */
    @Transactional
    public void deleteTask(UUID taskId, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        taskRepository.delete(task);
    }

    /**
     * Unico lugar que muda o status de uma Task. Quando o novo status e
     * DONE, completedAt e preenchido; em qualquer outro status,
     * completedAt volta a null (task reaberta nao deveria continuar
     * marcada como concluida em tal data).
     *
     * Validacao de transicoes invalidas (ex: DONE -> CANCELLED sem passar
     * por IN_PROGRESS) fica deliberadamente fora desta versao - exige
     * definir a maquina de estados antes de fazer sentido. Por ora,
     * qualquer transicao e aceita.
     */
    @Transactional
    public TaskResponseDTO updateTaskStatus(UUID taskId, UUID userId, UpdateTaskStatusRequestDTO dto) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        task.setStatus(dto.getStatus());
        task.setCompletedAt(dto.getStatus() == Status.DONE ? Instant.now() : null);

        Task savedTask = taskRepository.save(task);
        return toResponseDTO(savedTask);
    }

    // ===========================================
    // METODOS DE SUBTASK
    // ===========================================

    @Transactional
    public SubTaskResponseDTO addSubtask(UUID taskId, SubTaskRequestDTO dto, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        SubTask subTask = SubTask.builder()
                .title(dto.getTitle())
                .task(task)
                .build();

        SubTask savedSubTask = subTaskRepository.save(subTask);
        return toResponseDTO(savedSubTask);
    }

    /**
     * Duas checagens de posse em cadeia, nao uma so: primeiro que a Task
     * pertence ao usuario, depois que a SubTask pertence especificamente
     * a essa Task (findByIdAndTaskId). Sem o segundo passo, um usuario
     * dono da Task A poderia mandar o id de uma subtask da Task B (de
     * outro usuario) e editar ela, contanto que soubesse o UUID - IDOR
     * no nivel de subtask, nao so de task.
     */
    @Transactional
    public SubTaskResponseDTO updateSubtask(UUID taskId, UUID subtaskId, SubTaskRequestDTO dto, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        SubTask subTask = subTaskRepository.findByIdAndTaskId(subtaskId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));

        subTask.setTitle(dto.getTitle());

        SubTask savedSubTask = subTaskRepository.save(subTask);
        return toResponseDTO(savedSubTask);
    }

    @Transactional
    public SubTaskResponseDTO toggleSubtaskCompletion(UUID taskId, UUID subtaskId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        SubTask subTask = subTaskRepository.findByIdAndTaskId(subtaskId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));

        subTask.setCompleted(!subTask.isCompleted());

        SubTask savedSubTask = subTaskRepository.save(subTask);
        return toResponseDTO(savedSubTask);
    }

    @Transactional
    public void deleteSubtask(UUID taskId, UUID subtaskId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        SubTask subTask = subTaskRepository.findByIdAndTaskId(subtaskId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));

        subTaskRepository.delete(subTask);
    }

    // ===========================================
    // CONVERSAO ENTITY -> DTO (privados)
    // ===========================================

    private TaskResponseDTO toResponseDTO(Task task) {
        TaskResponseDTO dto = new TaskResponseDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setPriority(task.getPriority());
        dto.setStatus(task.getStatus());
        dto.setDueDate(task.getDueDate());
        dto.setCompletedAt(task.getCompletedAt());
        dto.setUserId(task.getUser().getId());
        dto.setUserName(task.getUser().getName());
        dto.setSubTasks(
                task.getSubTasks().stream()
                        .map(this::toResponseDTO)
                        .collect(Collectors.toList())
        );
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        return dto;
    }

    private SubTaskResponseDTO toResponseDTO(SubTask subTask) {
        SubTaskResponseDTO dto = new SubTaskResponseDTO();
        dto.setId(subTask.getId());
        dto.setTitle(subTask.getTitle());
        dto.setCompleted(subTask.isCompleted());
        return dto;
    }
}
