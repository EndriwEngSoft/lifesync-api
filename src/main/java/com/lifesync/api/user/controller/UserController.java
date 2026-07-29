package com.lifesync.api.user.controller;

import com.lifesync.api.security.SecurityUser;
import com.lifesync.api.user.dto.UserRequestDTO;
import com.lifesync.api.user.dto.UserResponseDTO;
import com.lifesync.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints do proprio perfil ("/me"). So faz sentido pra quem ja esta
 * autenticado - por isso nao entra na lista de rotas publicas do
 * SecurityConfig. userId sempre vem do token, nunca de parametro.
 */
@Tag(name = "User", description = "Endpoints do perfil do usuario")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Retorna o perfil do usuario autenticado")
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getMe(@AuthenticationPrincipal SecurityUser securityUser) {
        return ResponseEntity.ok(userService.getMe(securityUser.getId()));
    }

    @Operation(summary = "Atualiza o perfil do usuario autenticado")
    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateMe(@Valid @RequestBody UserRequestDTO request,
                                                    @AuthenticationPrincipal SecurityUser securityUser) {
        return ResponseEntity.ok(userService.updateMe(securityUser.getId(), request));
    }
}
