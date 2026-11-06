package com.lifesync.api.user.service;

import com.lifesync.api.exception.DuplicateResourceException;
import com.lifesync.api.exception.InvalidTimezoneException;
import com.lifesync.api.user.dto.UserRequestDTO;
import com.lifesync.api.user.dto.UserResponseDTO;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o perfil proprio: leitura, edicao com validacao de email/username
 * duplicado (excluindo o proprio usuario da checagem) e validacao de
 * timezone contra os IDs IANA reais.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .name("Nome")
                .username("usuario")
                .email("user@lifesync.com")
                .passwordHash("hash")
                .timezone("America/Sao_Paulo")
                .active(true)
                .build();
    }

    @Test
    void registerUser_WithDuplicateEmail_ThrowsAndDoesNotSave() {
        when(userRepository.findByEmail("user@lifesync.com")).thenReturn(Optional.of(user));
        User newUser = User.builder().name("Novo").username("novo").email("user@lifesync.com")
                .passwordHash("senha123").build();

        assertThrows(DuplicateResourceException.class, () -> userService.registerUser(newUser));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_WithDuplicateUsername_ThrowsAndDoesNotSave() {
        when(userRepository.findByEmail("novo@lifesync.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("usuario")).thenReturn(true);
        User newUser = User.builder().name("Novo").username("usuario").email("novo@lifesync.com")
                .passwordHash("senha123").build();

        DuplicateResourceException exception =
                assertThrows(DuplicateResourceException.class, () -> userService.registerUser(newUser));

        assertEquals("Username already exists.", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_WithFreeEmailAndUsername_HashesPasswordAndSaves() {
        when(userRepository.findByEmail("novo@lifesync.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("novo")).thenReturn(false);
        when(passwordEncoder.encode("senha123")).thenReturn("hash-bcrypt");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User newUser = User.builder().name("Novo").username("novo").email("novo@lifesync.com")
                .passwordHash("senha123").build();

        User saved = userService.registerUser(newUser);

        assertEquals("hash-bcrypt", saved.getPasswordHash());
    }

    @Test
    void getMe_ReturnsCurrentUserProfile() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponseDTO response = userService.getMe(userId);

        assertEquals(userId, response.getId());
        assertEquals("Nome", response.getName());
        assertEquals("usuario", response.getUsername());
        assertEquals("user@lifesync.com", response.getEmail());
        assertEquals("America/Sao_Paulo", response.getTimezone());
    }

    @Test
    void updateMe_WithDuplicateEmail_ThrowsException() {
        UserRequestDTO request = new UserRequestDTO();
        request.setName("Novo Nome");
        request.setUsername("novo_usuario");
        request.setEmail("novo@lifesync.com");
        request.setTimezone("America/Sao_Paulo");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("novo@lifesync.com", userId)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> userService.updateMe(userId, request));
    }

    @Test
    void updateMe_PersistsAllowedFields() {
        UserRequestDTO request = new UserRequestDTO();
        request.setName("Novo Nome");
        request.setUsername("novo_usuario");
        request.setEmail("novo@lifesync.com");
        request.setTimezone("America/Fortaleza");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot(any(), any())).thenReturn(false);
        when(userRepository.existsByUsernameAndIdNot(any(), any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO response = userService.updateMe(userId, request);

        assertEquals("Novo Nome", response.getName());
        assertEquals("novo_usuario", response.getUsername());
        assertEquals("novo@lifesync.com", response.getEmail());
        assertEquals("America/Fortaleza", response.getTimezone());
    }

    @Test
    void updateMe_WithInvalidTimezone_ThrowsException() {
        UserRequestDTO request = new UserRequestDTO();
        request.setName("Novo Nome");
        request.setUsername("novo_usuario");
        request.setEmail("novo@lifesync.com");
        request.setTimezone("invalid/timezone");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot(any(), any())).thenReturn(false);
        when(userRepository.existsByUsernameAndIdNot(any(), any())).thenReturn(false);

        assertThrows(InvalidTimezoneException.class, () -> userService.updateMe(userId, request));
    }
}
