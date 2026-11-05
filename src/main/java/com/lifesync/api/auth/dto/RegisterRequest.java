package com.lifesync.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dados de entrada do cadastro. Separado da entidade User de proposito
 * (mass assignment) - o cliente so pode preencher esses campos, nunca
 * role/active/id diretamente.
 *
 * Os limites de tamanho acompanham as colunas do banco (VARCHAR(255)) e o
 * UserRequestDTO: sem eles, um texto longo demais passava na validacao e
 * estourava so no INSERT, virando erro 500.
 */
@Getter
@Setter
@NoArgsConstructor
public class RegisterRequest {

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

    /**
     * O maximo de 72 existe porque o BCrypt so considera os primeiros 72
     * bytes da senha - aceitar mais que isso daria a falsa impressao de
     * que a senha inteira esta sendo usada.
     */
    @NotBlank
    @Size(min = 6, max = 72)
    private String password;

}
