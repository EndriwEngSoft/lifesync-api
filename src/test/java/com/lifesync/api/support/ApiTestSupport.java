package com.lifesync.api.support;

import com.lifesync.api.security.JwtTokenProvider;
import com.lifesync.api.security.SecurityUser;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

/**
 * Apoio dos testes de API com seguranca real: cria usuarios no banco de teste
 * e gera tokens JWT de acesso validos para eles.
 */
public abstract class ApiTestSupport {

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    protected User newUser(String tag) {
        String unique = tag + "-" + UUID.randomUUID();
        return userRepository.saveAndFlush(User.builder().name("Nome " + tag).username(unique)
                .email(unique + "@teste.com").passwordHash("hash").timezone("America/Sao_Paulo").build());
    }

    protected String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(new SecurityUser(user));
    }
}
