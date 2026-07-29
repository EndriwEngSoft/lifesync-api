package com.lifesync.api.user.service;

import com.lifesync.api.exception.ResourceNotFoundException;
import com.lifesync.api.exception.DuplicateResourceException;
import com.lifesync.api.exception.InvalidTimezoneException;
import com.lifesync.api.user.dto.UserRequestDTO;
import com.lifesync.api.user.dto.UserResponseDTO;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastro (usado pelo AuthService) e gerenciamento do proprio perfil
 * ("/me"). Nunca busca ou edita outro usuario que nao seja o dono da
 * sessao autenticada.
 */
@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Cadastra um novo usuario, aplicando hash na senha antes de persistir.
     * A mensagem de conflito e generica de proposito: nao repete o email
     * de volta na resposta, pra nao dar pra um atacante descobrir contas
     * existentes so testando o endpoint em massa.
     */
    public User registerUser(User user) {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new DuplicateResourceException("Email already exists.");
        }
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        return userRepository.save(user);
    }

    /**
     * Devolve Optional em vez de lancar ResourceNotFoundException aqui
     * de proposito: quem chama pode ter um motivo diferente pra tratar
     * "nao encontrado" (ex: AuthService.refreshToken usa uma mensagem
     * especifica pro contexto de renovacao de token).
     */
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return toResponseDTO(user);
    }

    /**
     * Email e username checados excluindo o proprio usuario
     * (existsBy...AndIdNot) - senao salvar o proprio perfil sem mudar
     * esses campos sempre acusaria "ja existe" contra si mesmo.
     * Timezone validado contra os IDs IANA reais que java.time.ZoneId
     * aceita, rejeitando qualquer string que nao seja um fuso valido.
     */
    @Transactional
    public UserResponseDTO updateMe(UUID userId, UserRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (userRepository.existsByEmailAndIdNot(request.getEmail(), userId)) {
            throw new DuplicateResourceException("Email already exists.");
        }

        if (userRepository.existsByUsernameAndIdNot(request.getUsername(), userId)) {
            throw new DuplicateResourceException("Username already exists.");
        }

        String timezone = validateTimezone(request.getTimezone());

        user.setName(request.getName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setTimezone(timezone);

        return toResponseDTO(userRepository.save(user));
    }

    private UserResponseDTO toResponseDTO(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setActive(user.isActive());
        dto.setTimezone(user.getTimezone());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        return dto;
    }

    /**
     * ZoneId.of lanca DateTimeException pra qualquer string que nao seja
     * um ID IANA reconhecido - convertido aqui pra InvalidTimezoneException
     * (400), em vez de deixar a exception generica cair no catch-all (500).
     */
    private String validateTimezone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException e) {
            throw new InvalidTimezoneException("Invalid timezone: " + timezone);
        }
    }
}
