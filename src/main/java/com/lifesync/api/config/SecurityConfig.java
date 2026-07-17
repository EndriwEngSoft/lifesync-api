package com.lifesync.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    /**
     * BCrypt aplica um hash com "salt" aleatorio embutido — duas senhas
     * iguais geram hashes diferentes. Isso protege contra ataques de
     * rainbow table e ja e o padrao recomendado pelo proprio Spring Security.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
