package org.example.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.example.config.JwtProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateAccessToken(UserDetails user) {
        return buildToken(
                user,
                jwtProperties.getAccessTokenExpiration().toMillis(),
                true
        );
    }

    public String generateRefreshToken(UserDetails user) {
        return buildToken(
                user,
                jwtProperties.getRefreshTokenExpiration().toMillis(),
                false
        );
    }

    private String buildToken(
            UserDetails user,
            long expirationMillis,
            boolean includeRoles
    ) {
        Map<String, Object> claims = new HashMap<>();

        if (includeRoles) {
            claims.put(
                    "roles",
                    user.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList())
            );
        }

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername()) // email
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public List<GrantedAuthority> extractRoles(String token) {
        List<String> roles = parseClaims(token).get("roles", List.class);
        if (roles == null) return Collections.emptyList();

        return roles.stream()
                .map(role -> (GrantedAuthority) () -> role)
                .collect(Collectors.toList());
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
