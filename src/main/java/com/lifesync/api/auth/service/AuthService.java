package com.lifesync.api.auth.service;

import com.lifesync.api.auth.dto.AuthResponse;
import com.lifesync.api.auth.dto.LoginRequest;
import com.lifesync.api.auth.dto.RegisterRequest;
import com.lifesync.api.exception.InactiveAccountException;
import com.lifesync.api.exception.InvalidTokenException;
import com.lifesync.api.exception.ResourceNotFoundException;
import com.lifesync.api.security.JwtTokenProvider;
import com.lifesync.api.security.SecurityUser;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orquestra o fluxo de autenticacao: cadastro, login e renovacao de token.
 * Nao duplica regra de negocio que ja existe em outro lugar - delega
 * validacao de email duplicado e hash de senha pro UserService, e
 * validacao de credenciais pro AuthenticationManager do Spring Security.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtTokenProvider  jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    /**
     * Cadastra o usuario via UserService (que ja cuida de email duplicado
     * e hash de senha) e devolve os tokens de acesso, dispensando um
     * segundo login logo apos o registro.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        User userToSave = User.builder()
                .name(request.getName())
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(request.getPassword())
                .build();

        User savedUser = userService.registerUser(userToSave);
        SecurityUser securityUser = new SecurityUser(savedUser);

        String accessToken = jwtTokenProvider.generateAccessToken(securityUser);
        String refreshToken = jwtTokenProvider.generateRefreshToken(securityUser);

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);

        return response;
    }

    /**
     * Autentica via AuthenticationManager, que por baixo dos panos chama
     * UserDetailsServiceImpl + PasswordEncoder e mascara "email nao existe"
     * vs "senha errada" numa unica BadCredentialsException. O principal
     * retornado ja e o SecurityUser autenticado - nao precisa de um novo
     * findByEmail aqui.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword());
        Authentication authentication = authenticationManager.authenticate(authToken);

        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();

        String accessToken = jwtTokenProvider.generateAccessToken(securityUser);
        String refreshToken = jwtTokenProvider.generateRefreshToken(securityUser);

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);

        return response;
    }

    /**
     * Gera um novo par de tokens a partir de um refresh token valido.
     * Recusa token invalido/expirado ou do tipo errado (InvalidTokenException)
     * e conta desativada (InactiveAccountException) - sem essas checagens,
     * um token adulterado cairia no catch-all generico (500) em vez de um
     * 401 claro, e uma conta banida continuaria renovando token
     * indefinidamente enquanto tivesse um refresh token ainda valido.
     */
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new InvalidTokenException("Invalid or expired refresh token.");
        }

        String userIdStr = jwtTokenProvider.getUserIdFromToken(refreshToken);
        UUID userId = UUID.fromString(userIdStr);

        User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (!user.isActive()) {
            throw new InactiveAccountException("Account is inactive. Cannot refresh token.");
        }

        SecurityUser securityUser = new SecurityUser(user);

        String newAccessToken = jwtTokenProvider.generateAccessToken(securityUser);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(securityUser);

        AuthResponse response = new AuthResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);

        return response;
    }
}
