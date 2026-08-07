package com.lifesync.api.user.controller;

import com.lifesync.api.common.ApiErrorResponse;
import com.lifesync.api.config.OpenApiConfig;
import com.lifesync.api.security.SecurityUser;
import com.lifesync.api.user.dto.UserRequestDTO;
import com.lifesync.api.user.dto.UserResponseDTO;
import com.lifesync.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
 *
 * 401 nao e documentado endpoint a endpoint aqui de proposito: o
 * {@code @SecurityRequirement} da classe ja deixa isso implicito pra
 * qualquer metodo do controller (o cadeado aparece no Swagger UI) -
 * repetir "token invalido" em cada @ApiResponses seria a mesma
 * informacao 19 vezes, sem nada de especifico por endpoint.
 */
@Tag(name = "User", description = "Endpoints do perfil do usuario")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Retorna o perfil do usuario autenticado")
    @ApiResponse(responseCode = "200", description = "Perfil retornado")
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getMe(@AuthenticationPrincipal SecurityUser securityUser) {
        return ResponseEntity.ok(userService.getMe(securityUser.getId()));
    }

    @Operation(summary = "Atualiza o perfil do usuario autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado"),
            @ApiResponse(responseCode = "400", description = "Dado inválido (timezone fora do padrão IANA, campo obrigatório ausente)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email ou username já usado por outro usuário",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateMe(@Valid @RequestBody UserRequestDTO request,
                                                    @AuthenticationPrincipal SecurityUser securityUser) {
        return ResponseEntity.ok(userService.updateMe(securityUser.getId(), request));
    }
}
