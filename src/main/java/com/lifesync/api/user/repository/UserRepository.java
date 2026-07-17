package com.lifesync.api.user.repository;

import com.lifesync.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Usado no cadastro (checar email duplicado) e no login (autenticar por email).
    Optional<User> findByEmail(String email);

}
