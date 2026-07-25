package com.lifesync.api.auth.controller;

import com.lifesync.api.auth.dto.AuthResponse;
import com.lifesync.api.auth.dto.LoginRequest;
import com.lifesync.api.auth.dto.RegisterRequest;
import com.lifesync.api.auth.service.AuthService;
import com.lifesync.api.security.JwtTokenProvider;
import com.lifesync.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de fatia web (@WebMvcTest) - sobe so a camada MVC pro
 * AuthController, sem banco e sem o restante da aplicacao. AuthService
 * e mockado (@MockitoBean) porque so queremos validar o mapeamento
 * HTTP <-> JSON do controller, nao a logica de negocio (ja coberta em
 * outro teste). JwtTokenProvider e UserRepository tambem precisam ser
 * mockados porque o SecurityConfig e' carregado no contexto de teste, e
 * suas beans (JwtAuthFilter) dependem deles no construtor - mesmo com
 * addFilters=false desligando a execucao dos filtros na requisicao, o
 * Spring ainda monta o grafo de beans normalmente.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider  jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void register_WithValidRequest_Returns201Created()  throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("name");
        request.setUsername("username");
        request.setPassword("password");
        request.setEmail("teste@lifesync.com");

        AuthResponse fakeResponse = new  AuthResponse();
        fakeResponse.setAccessToken("fake-access-token");
        fakeResponse.setRefreshToken("fake-refresh-token");

        when(authService.register(any(RegisterRequest.class))).thenReturn(fakeResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("fake-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("fake-refresh-token"));
    }

    @Test
    void login_WithValidRequest_Returns200AndToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("teste@lifesync.com");
        request.setPassword("senha123");

        AuthResponse fakeResponse = new AuthResponse();
        fakeResponse.setAccessToken("fake-access-token");
        fakeResponse.setRefreshToken("fake-refresh-token");

        when(authService.login(any(LoginRequest.class))).thenReturn(fakeResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("fake-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("fake-refresh-token"));
    }

    @Test
    void refreshToken_WithValidRequest_Returns200AndToken() throws Exception {
        AuthResponse fakeResponse = new AuthResponse();
        fakeResponse.setAccessToken("fake-access-token");
        fakeResponse.setRefreshToken("fake-refresh-token");

        String headerValue = "Bearer fake-refresh-token";

        when(authService.refreshToken("fake-refresh-token")).thenReturn(fakeResponse);

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", headerValue))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("fake-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("fake-refresh-token"));
    }
}