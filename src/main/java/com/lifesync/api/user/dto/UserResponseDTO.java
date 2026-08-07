package com.lifesync.api.user.dto;

import com.lifesync.api.user.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Resposta do perfil do usuario autenticado.
 * Nao expõe senha e inclui os metadados relevantes do perfil.
 */
@Getter
@Setter
@NoArgsConstructor
public class UserResponseDTO {

    @Schema(description = "UUID do usuário")
    private UUID id;

    @Schema(description = "Nome do usuário", example = "Endri")
    private String name;

    @Schema(description = "Nome de usuário único", example = "endri")
    private String username;

    @Schema(description = "Email do usuário", example = "endri@example.com")
    private String email;

    @Schema(description = "Papel do usuário")
    private User.Role role;

    @Schema(description = "Se a conta está ativa")
    private boolean active;

    @Schema(description = "Timezone IANA do usuário", example = "America/Sao_Paulo")
    private String timezone;

    @Schema(description = "Data de criação")
    private Instant createdAt;

    @Schema(description = "Data da última atualização")
    private Instant updatedAt;
}
