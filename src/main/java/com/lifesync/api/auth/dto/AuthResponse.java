package com.lifesync.api.auth.dto;

import lombok.*;

/**
 * Resposta devolvida no cadastro, login e renovacao de token.
 * accessToken vai no header Authorization das proximas requisicoes;
 * refreshToken so serve pra pedir um novo accessToken quando ele expirar.
 */
@Getter
@Setter
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
}
