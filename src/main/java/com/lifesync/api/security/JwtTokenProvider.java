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
 *
 * Access e refresh token tem a mesma estrutura, so a expiracao muda -
 * por isso carregam um claim "type" (access/refresh). Sem esse claim, um
 * refresh token vazado poderia ser usado como Bearer token em qualquer
 * rota protegida, valendo pelos 7 dias inteiros em vez das 24h pensadas
 * pro access token.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

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
        return generateToken(userDetails, jwtExpirationMs, TOKEN_TYPE_ACCESS);
    }

    public String generateRefreshToken(SecurityUser userDetails) {
        return generateToken(userDetails, jwtRefreshExpirationMs, TOKEN_TYPE_REFRESH);
    }

    private String generateToken(SecurityUser userDetails, long expirationMs, String tokenType) {
        return Jwts.builder()
                .subject(userDetails.getId().toString())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
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

    /**
     * True somente se o token for do tipo access. Usado pelo JwtAuthFilter
     * pra recusar um refresh token apresentado como Bearer token em rotas
     * protegidas - so validar assinatura/expiracao nao seria suficiente,
     * porque um refresh token tambem passa nessas duas checagens.
     */
    public boolean isAccessToken(String token) {
        return TOKEN_TYPE_ACCESS.equals(getTokenType(token));
    }

    /**
     * True somente se o token for do tipo refresh. Usado no endpoint de
     * refresh pra recusar um access token sendo usado no lugar errado.
     */
    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(getTokenType(token));
    }

    private String getTokenType(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get(CLAIM_TOKEN_TYPE, String.class);
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
