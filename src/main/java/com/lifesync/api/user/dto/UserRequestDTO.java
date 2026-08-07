package com.lifesync.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

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
    @Schema(description = "Nome do usuário", example = "Endri")
    private String name;

    @NotBlank
    @Size(max = 150)
    @Schema(description = "Nome de usuário único", example = "endri")
    private String username;

    @NotBlank
    @Email
    @Size(max = 255)
    @Schema(description = "Email do usuário", example = "endri@example.com")
    private String email;

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Timezone IANA do usuário", example = "America/Sao_Paulo")
    private String timezone;
}
