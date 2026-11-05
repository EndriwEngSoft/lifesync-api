package com.lifesync.api.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Garante que os limites de tamanho do cadastro acompanham as colunas do
 * banco: sem eles, um valor longo demais passava na validacao e so falhava
 * no INSERT (erro 500).
 */
class RegisterRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validRequest_HasNoViolations() {
        assertTrue(validator.validate(request("Nome", "usuario", "user@lifesync.com", "senha123")).isEmpty());
    }

    @Test
    void nameLongerThan150_IsRejectedBySize() {
        assertRejectedOnlyBySizeOn("name", request("a".repeat(151), "usuario", "user@lifesync.com", "senha123"));
    }

    @Test
    void usernameLongerThan150_IsRejectedBySize() {
        assertRejectedOnlyBySizeOn("username", request("Nome", "a".repeat(151), "user@lifesync.com", "senha123"));
    }

    /**
     * O e-mail precisa ser valido para o {@code @Email} (parte local ate 64
     * caracteres, labels do dominio ate 63) e ainda assim ter mais de 255
     * caracteres: so assim a falha so pode vir do {@code @Size}. 64 + "@" +
     * 3 labels de 63 com ponto + label de 51 + ".com" = 312 caracteres.
     */
    @Test
    void emailLongerThan255_IsRejectedBySize() {
        String label63 = "b".repeat(63);
        String longEmail = "a".repeat(64) + "@" + label63 + "." + label63 + "." + label63 + "." + "c".repeat(51) + ".com";
        assertEquals(312, longEmail.length(), "o e-mail do teste deveria ter 312 caracteres");

        assertRejectedOnlyBySizeOn("email", request("Nome", "usuario", longEmail, "senha123"));
    }

    @Test
    void passwordLongerThan72_IsRejectedBySize() {
        assertRejectedOnlyBySizeOn("password", request("Nome", "usuario", "user@lifesync.com", "a".repeat(73)));
    }

    @Test
    void passwordShorterThan6_IsRejectedBySize() {
        assertRejectedOnlyBySizeOn("password", request("Nome", "usuario", "user@lifesync.com", "12345"));
    }

    /**
     * Exige pelo menos uma violacao e que TODAS sejam de {@code @Size} no
     * campo informado - assim o teste falha se outra regra (ex.: {@code @Email})
     * for quem rejeitou o valor, em vez de passar por engano.
     */
    private void assertRejectedOnlyBySizeOn(String field, RegisterRequest request) {
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "esperava ao menos 1 violacao em '" + field + "'");
        violations.forEach(violation -> {
            assertEquals(field, violation.getPropertyPath().toString(), "violacao em campo inesperado");
            assertEquals(Size.class, violation.getConstraintDescriptor().getAnnotation().annotationType(),
                    "esperava que a violacao viesse de @Size");
        });
    }

    private RegisterRequest request(String name, String username, String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setName(name);
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
