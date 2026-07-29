package com.lifesync.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dados de entrada do login. Senha so tem {@code @NotBlank}, sem
 * {@code @Size} - validacao de tamanho minimo e politica pra senha nova
 * (cadastro), nao pra senha ja existente que so esta sendo conferida
 * contra o hash salvo.
 */
@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

}