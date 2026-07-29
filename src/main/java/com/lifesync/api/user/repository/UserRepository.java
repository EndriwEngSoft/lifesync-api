package com.lifesync.api.user.repository;

import com.lifesync.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Usado no cadastro para checar email duplicado e no login para
     * autenticar o usuario.
     */
    Optional<User> findByEmail(String email);

    /**
     * "AndIdNot" exclui o proprio usuario da checagem - essencial pro
     * updateMe: sem isso, salvar o perfil sem mudar email/username
     * acusaria duplicidade contra si mesmo.
     */
    boolean existsByEmailAndIdNot(String email, UUID id);

    boolean existsByUsernameAndIdNot(String username, UUID id);

}
