package com.lifesync.api.auth.controller;

import com.lifesync.api.auth.dto.AuthResponse;
import com.lifesync.api.auth.dto.LoginRequest;
import com.lifesync.api.auth.dto.RegisterRequest;
import com.lifesync.api.auth.service.AuthService;
import com.lifesync.api.common.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário criado"),
            @ApiResponse(responseCode = "400", description = "Dado inválido (campo obrigatório ausente, formato incorreto)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email ou username já cadastrado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);

        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "Autentica um usuário e retorna tokens de acesso")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dado inválido (campo obrigatório ausente)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Email ou senha incorretos",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Novo par de tokens gerado"),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido, expirado ou de tipo errado (access no lugar de refresh)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Conta desativada",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("Authorization") String refreshToken) {
        String token = refreshToken.startsWith("Bearer ")
                ? refreshToken.substring(7)
                : refreshToken;

        AuthResponse response = authService.refreshToken(token);
        return ResponseEntity.ok(response);
    }
}
