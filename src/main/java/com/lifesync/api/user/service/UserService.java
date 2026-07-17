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

    public void registerUser(User user) {
        // Mensagem generica de proposito (nao expoe o email de volta):
        // reduz o risco de "user enumeration" (descobrir quais emails
        // ja estao cadastrados testando o endpoint em massa).
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new DuplicateResourceException("Email already exists.");
        }
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        userRepository.save(user);
    }
}
