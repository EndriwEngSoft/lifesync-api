package com.lifesync.api.security;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Gera e valida os tokens JWT. Subject do token e o id do usuario (nao o
 * email) - assim o token continua valido mesmo que o usuario troque o
 * email antes de expirar.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration}")
    private long jwtRefreshExpirationMs;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = jwtSecret.getBytes(UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(SecurityUser userDetails) {
        return generateToken(userDetails, jwtExpirationMs);
    }

    public String generateRefreshToken(SecurityUser userDetails) {
        return generateToken(userDetails, jwtRefreshExpirationMs);
    }

    private String generateToken(SecurityUser userDetails, long expirationMs) {
        return Jwts.builder()
                .subject(userDetails.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Confere assinatura e expiracao. Qualquer JwtException (expirado,
     * assinatura invalida, formato quebrado) vira false - o chamador nao
     * precisa saber o motivo exato, so se pode confiar no token ou nao.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}