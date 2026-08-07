package com.lifesync.api.auth.dto;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resposta devolvida no cadastro, login e renovacao de token.
 * accessToken vai no header Authorization das proximas requisicoes;
 * refreshToken so serve pra pedir um novo accessToken quando ele expirar.
 */
@Getter
@Setter
public class AuthResponse {

    @Schema(description = "Access token JWT")
    private String accessToken;

    @Schema(description = "Refresh token JWT")
    private String refreshToken;
}
