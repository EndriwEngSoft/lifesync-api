package com.lifesync.api.security;

import com.lifesync.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * A UsernameNotFoundException aqui e a do Spring Security, nao uma
     * nossa — o DaoAuthenticationProvider reconhece esse tipo especifico
     * e converte pra "credenciais invalidas" na resposta, sem dizer se
     * o email existe ou nao (mesmo cuidado de enumeration que ja aplicamos
     * no cadastro).
     */
    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .map(SecurityUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
    }
}
