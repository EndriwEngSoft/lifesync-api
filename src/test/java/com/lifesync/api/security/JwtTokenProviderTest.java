package com.lifesync.api.security;

import com.lifesync.api.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste unitario puro (sem contexto Spring) - JwtTokenProvider nao
 * depende de banco nem de HTTP, so de logica pura de assinatura/expiracao,
 * entao nao precisa de @SpringBootTest nem de mocks. Os campos com @Value
 * sao preenchidos na mao via ReflectionTestUtils (o Spring so faz isso
 * de verdade dentro de um contexto real), e init() e chamado manualmente
 * ja que @PostConstruct tambem so dispara com o Spring gerenciando o bean.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();

        ReflectionTestUtils.setField(provider, "jwtSecret", "chave-secreta-de-teste-muito-segura-1234567890");
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 86400000L);
        ReflectionTestUtils.setField(provider, "jwtRefreshExpirationMs", 604800000L);

        provider.init();
    }


    private SecurityUser createTestUser() {
        UUID testUserId = UUID.randomUUID();

        User fakeUser = User.builder()
                .id(testUserId)
                .name("Usuário Teste")
                .email("teste@lifesync.com")
                .passwordHash("senha-falsa-123")
                .active(true)
                .build();

        return new SecurityUser(fakeUser);
    }

    @Test
    void accessTokenIsRecognizedAsAccessToken() {
        SecurityUser userDetails = createTestUser();

        String token = provider.generateAccessToken(userDetails);

        assertTrue(provider.isAccessToken(token), "The generated token should be recognized as an access token");

        assertFalse(provider.isRefreshToken(token), "An access token must not be recognized as a refresh token");
    }

    @Test
    void refreshTokenIsRecognizedAsRefreshToken() {
        SecurityUser userDetails = createTestUser();

        String token = provider.generateRefreshToken(userDetails);

        assertTrue(provider.isRefreshToken(token));
        assertFalse(provider.isAccessToken(token));
    }

    @Test
    void validateToken_WithValidToken_ReturnsTrue() {
        SecurityUser userDetails = createTestUser();
        String token = provider.generateAccessToken(userDetails);

        assertTrue(provider.validateToken(token), "A freshly generated, authentic token should return true");
    }

    @Test
    void validateToken_WithInvalidString_ReturnsFalse() {
        String invalidToken = "isso-nao-e-um-jwt-valido-123";

        assertFalse(provider.validateToken(invalidToken), "A random string must not be validated as a valid token");
    }

    @Test
    void validateToken_WithExpiredToken_ReturnsFalse() {
        SecurityUser userDetails = createTestUser();

        JwtTokenProvider expiredProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(expiredProvider, "jwtSecret", "chave-secreta-de-teste-muito-segura-1234567890");
        ReflectionTestUtils.setField(expiredProvider, "jwtExpirationMs", -1000L);
        ReflectionTestUtils.setField(expiredProvider, "jwtRefreshExpirationMs", -1000L);
        expiredProvider.init();

        String expiredToken = expiredProvider.generateAccessToken(userDetails);

        assertFalse(expiredProvider.validateToken(expiredToken), "An expired token should return false on validation");
    }

    @Test
    void getUserIdFromToken_ReturnsCorrectId() {
        SecurityUser userDetails = createTestUser();

        String token = provider.generateAccessToken(userDetails);

        String result = provider.getUserIdFromToken(token);

        assertEquals(userDetails.getId().toString(), result);
    }
}