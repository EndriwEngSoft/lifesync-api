package com.lifesync.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dados editáveis do perfil do proprio usuario.
 * Nao inclui id, role, active ou passwordHash de proposito.
 */
@Getter
@Setter
@NoArgsConstructor
public class UserRequestDTO {

    @NotBlank
    @Size(max = 150)
    private String name;

    @NotBlank
    @Size(max = 150)
    private String username;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(max = 255)
    private String timezone;
}
