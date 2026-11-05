package com.lifesync.api.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void nameLongerThan150_IsRejected() {
        assertSingleViolationOn("name", request("a".repeat(151), "usuario", "user@lifesync.com", "senha123"));
    }

    @Test
    void usernameLongerThan150_IsRejected() {
        assertSingleViolationOn("username", request("Nome", "a".repeat(151), "user@lifesync.com", "senha123"));
    }

    @Test
    void emailLongerThan255_IsRejected() {
        String longEmail = "a".repeat(250) + "@x.com";
        assertSingleViolationOn("email", request("Nome", "usuario", longEmail, "senha123"));
    }

    @Test
    void passwordLongerThan72_IsRejected() {
        assertSingleViolationOn("password", request("Nome", "usuario", "user@lifesync.com", "a".repeat(73)));
    }

    @Test
    void passwordShorterThan6_IsRejected() {
        assertSingleViolationOn("password", request("Nome", "usuario", "user@lifesync.com", "12345"));
    }

    private void assertSingleViolationOn(String field, RegisterRequest request) {
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "esperava ao menos 1 violacao em '" + field + "'");
        violations.forEach(violation ->
                assertEquals(field, violation.getPropertyPath().toString(), "violacao em campo inesperado"));
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
