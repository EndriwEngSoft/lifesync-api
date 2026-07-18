package com.lifesync.api.security;

import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Roda uma vez por request (OncePerRequestFilter), antes do
 * UsernamePasswordAuthenticationFilter padrao do Spring Security. Le o
 * header Authorization, valida o Bearer token e, se for valido, preenche
 * o SecurityContext com o usuario autenticado - dali pra frente o resto
 * da aplicacao (controllers, @PreAuthorize) enxerga a request como
 * autenticada normalmente.
 *
 * Se nao tiver token, ou o token for invalido, a request simplesmente
 * segue sem autenticacao - quem barra o acesso depois e o
 * authorizeHttpRequests do SecurityConfig.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        if (jwtTokenProvider.validateToken(token)) {
            try {
                String userIdString = jwtTokenProvider.getUserIdFromToken(token);
                UUID userId = UUID.fromString(userIdString);

                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));

                UserDetails userDetails = new SecurityUser(user);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // Token valido na assinatura, mas algo deu errado montando a
                // autenticacao (ex: usuario foi deletado depois do token ser
                // emitido). Nao propaga o erro - so segue sem autenticar,
                // e quem bloqueia o acesso depois e a regra de authorization.
                log.warn("Failed to authenticate user from JWT: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}