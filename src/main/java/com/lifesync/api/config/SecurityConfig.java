package com.lifesync.api.config;

import com.lifesync.api.security.JwtAuthFilter;
import com.lifesync.api.security.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    /**
     * BCrypt aplica um hash com "salt" aleatorio embutido — duas senhas
     * iguais geram hashes diferentes. Isso protege contra ataques de
     * rainbow table e ja e o padrao recomendado pelo proprio Spring Security.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * API stateless: sem sessao, sem cookie, entao CSRF nao se aplica aqui
     * (o ataque de CSRF depende de cookie de sessao pra funcionar). O unico
     * mecanismo de autenticacao e o Bearer token via JwtAuthFilter — nao
     * ha Basic Auth nem form login habilitado, de proposito.
     *
     * So cadastro/login/refresh e a documentacao Swagger sao publicos.
     * "/api/users/me" nao entra na lista de liberados de proposito: o
     * proprio endpoint so faz sentido pra quem ja esta autenticado.
     *
     * "/actuator/health" tambem e publico, de proposito: probes de
     * liveness/readiness de orquestradores (Kubernetes, Docker Swarm,
     * ECS) normalmente nao mandam nenhuma credencial, e exigir Bearer
     * token aqui derrubaria o healthcheck assim que o projeto for
     * containerizado. So esse path entra na lista - o resto de
     * "/actuator/**" (env, beans, mappings etc.) continua exigindo
     * autenticacao, porque esses endpoints expoe detalhe interno da
     * aplicacao que nao deveria ser publico.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                "/api/auth/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}