package br.edu.simpi.autenticacao;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expiration) {

        // String secret = "bwyPKPnrMYRkATq7cdlkUrXFSogx0FV26nG0M2gtvpQ=";
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
    }

    public String gerarToken(Long userId) {
        try {
            Date agora = new Date();

            Date expiracao = new Date(
                    agora.getTime() + expiration);

            return Jwts.builder()
                    .subject(userId.toString())
                    .issuedAt(agora)
                    .expiration(expiracao)
                    .signWith(secretKey, Jwts.SIG.HS256)
                    .compact();
        } catch (JwtException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erro ao gerar token de autenticação", e);
        }
    }

    public Long validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long getExpirationSeconds() {
        return expiration / 1000;
    }
}
