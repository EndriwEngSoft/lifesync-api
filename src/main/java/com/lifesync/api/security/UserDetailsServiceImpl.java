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

    // UsernameNotFoundException e' a excecao especifica do Spring Security
    // (nao uma nossa) - o DaoAuthenticationProvider reconhece esse tipo
    // internamente e a traduz em "credenciais invalidas" de forma generica,
    // evitando revelar se o email existe ou nao (mesmo cuidado de
    // user enumeration que aplicamos no registro).
    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .map(SecurityUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
    }
}
