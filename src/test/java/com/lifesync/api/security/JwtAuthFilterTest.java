package com.lifesync.api.security;

import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Instancia o filtro na mao (sem contexto Spring) e chama o doFilter
 * publico herdado de OncePerRequestFilter, exercitando o mesmo caminho
 * que uma requisicao real percorreria. Confere que uma conta desativada
 * e barrada com 403 e NUNCA chega a chamar o proximo filtro da cadeia.
 */
class JwtAuthFilterTest {

    @Test
    void doFilter_WithInactiveUser_Returns403() throws Exception {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        UserRepository userRepository = mock(UserRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        JwtAuthFilter filter = new JwtAuthFilter(tokenProvider, userRepository, objectMapper);

        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@lifesync.com")
                .passwordHash("hash")
                .active(false)
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer fake-token");
        request.setRequestURI("/api/tasks");

        MockHttpServletResponse response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);

        when(tokenProvider.validateToken("fake-token")).thenReturn(true);
        when(tokenProvider.isAccessToken("fake-token")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("fake-token")).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertFalse(response.getContentAsString().isBlank());
        verify(chain, never()).doFilter(any(), any());
    }
}
