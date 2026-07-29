package com.lifesync.api.habit.controller;

import com.lifesync.api.habit.dto.HabitHistoryResponseDTO;
import com.lifesync.api.habit.dto.HabitRequestDTO;
import com.lifesync.api.habit.dto.HabitResponseDTO;
import com.lifesync.api.habit.service.HabitService;
import com.lifesync.api.security.SecurityUser;
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
 * Endpoints de Habito e historico de check-ins. Todos extraem userId via
 * {@code @AuthenticationPrincipal}, nunca do corpo da requisicao. Toda
 * logica de negocio (streak, soft delete) fica no HabitService, nao aqui.
 */
@Tag(name = "Habit", description = "Endpoints de Habitos")
@RestController
@RequestMapping("/api/habits")
@RequiredArgsConstructor
public class HabitController {

    private final HabitService habitService;

    @PostMapping
    public ResponseEntity<HabitResponseDTO> createHabit(@Valid @RequestBody HabitRequestDTO request,
                                                        @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.status(CREATED).body(habitService.createHabit(request, userId));
    }

    @GetMapping("/{habitId}")
    public ResponseEntity<HabitResponseDTO> getHabitById(@PathVariable UUID habitId,
                                                         @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(habitService.getHabitById(habitId, userId));
    }

    @GetMapping
    public ResponseEntity<Page<HabitResponseDTO>> getAllHabits(Pageable pageable,
                                                               @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(habitService.getAllHabits(userId, pageable));
    }

    @PutMapping("/{habitId}")
    public ResponseEntity<HabitResponseDTO> updateHabit(@PathVariable UUID habitId,
                                                        @Valid @RequestBody HabitRequestDTO request,
                                                        @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(habitService.updateHabit(habitId, request, userId));
    }

    @DeleteMapping("/{habitId}")
    public ResponseEntity<Void> deleteHabit(@PathVariable UUID habitId,
                                            @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        habitService.deleteHabit(habitId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{habitId}/checkin")
    public ResponseEntity<HabitResponseDTO> checkIn(@PathVariable UUID habitId,
                                                    @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.status(CREATED).body(habitService.checkIn(habitId, userId));
    }

    @GetMapping("/{habitId}/history")
    public ResponseEntity<Page<HabitHistoryResponseDTO>> getHistory(@PathVariable UUID habitId,
                                                                     Pageable pageable,
                                                                     @AuthenticationPrincipal SecurityUser securityUser) {
        UUID userId = securityUser.getId();
        return ResponseEntity.ok(habitService.getHistory(habitId, userId, pageable));
    }
}
