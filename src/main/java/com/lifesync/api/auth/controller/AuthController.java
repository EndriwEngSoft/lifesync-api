package com.lifesync.api.auth.controller;

import com.lifesync.api.auth.dto.AuthResponse;
import com.lifesync.api.auth.dto.LoginRequest;
import com.lifesync.api.auth.dto.RegisterRequest;
import com.lifesync.api.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * Endpoints publicos de autenticacao - registrados como permitAll no
 * SecurityConfig. Nenhuma regra de negocio aqui, so delega pro AuthService.
 */
@Tag(name = "Auth", description = "Endpoints de autenticação")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Registra um novo usuário e retorna tokens de acesso")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);

        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "Autentica um usuário e retorna tokens de acesso")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    /**
     * O refresh token e enviado no mesmo header Authorization usado pro
     * access token nas outras requisicoes, so que aqui o valor esperado
     * e o refresh token, nao o access token.
     */
    @Operation(summary = "Renova o token de acesso usando o refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("Authorization") String refreshToken) {
        String token = refreshToken.startsWith("Bearer ")
                ? refreshToken.substring(7)
                : refreshToken;

        AuthResponse response = authService.refreshToken(token);
        return ResponseEntity.ok(response);
    }
}
