package com.lifesync.api.config;

import com.lifesync.api.security.JwtAuthFilter;
import com.lifesync.api.security.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String corsAllowedOrigins;

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
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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

    /**
     * Origens em CSV via variavel de ambiente, nao lista fixa no codigo -
     * front de dev roda em portas diferentes de front de producao, e essa
     * config muda por ambiente sem precisar recompilar. O default aqui
     * cobre so localhost (Vite/CRA), pensado pra rodar local sem
     * configurar nada; em producao a env var e obrigatoria (ver
     * application-prod.yml, sem valor de fallback la de proposito).
     *
     * Nao da pra usar "*" mesmo que quisesse: com allowCredentials(true),
     * Spring Security rejeita wildcard em runtime - a spec do CORS proibe
     * essa combinacao (credenciais + qualquer origem seria um buraco de
     * seguranca). Isso forca a lista a ser sempre explicita.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}