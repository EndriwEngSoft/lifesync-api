package com.lifesync.api.user.dto;

import com.lifesync.api.user.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    private UUID id;
    private String name;
    private String username;
    private String email;
    private User.Role role;
    private boolean active;
    private String timezone;
    private Instant createdAt;
    private Instant updatedAt;
}
