package com.lifesync.api.user.service;

import com.lifesync.api.exception.DuplicateResourceException;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.security.crypto.password.PasswordEncoder;

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
    public void registerUser(User user) {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new DuplicateResourceException("Email already exists.");
        }
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        userRepository.save(user);
    }
}
