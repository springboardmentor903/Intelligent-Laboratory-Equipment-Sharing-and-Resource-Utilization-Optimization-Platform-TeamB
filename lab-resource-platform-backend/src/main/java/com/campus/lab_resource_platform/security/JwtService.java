package com.campus.lab_resource_platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtService {
    private static final String SECRET_KEY = "mysecretkeymysecretkeymysecretkey12";

    private final SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    public String generateToken(String email, String role){
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() * 1000 * 60 * 60))
                .signWith(
                        key,
                        SignatureAlgorithm.HS256
                )
                .compact();
    }
    public boolean validateToken(String token){
        try{
            extractAllClaims(token);
            return true;
        }
        catch(Exception e){
            return false;
        }
    }
    public String extractEmailByToken(String token){
        return extractAllClaims(token).getSubject();
    }
    public Claims extractAllClaims(String token){
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    public String extractRoleByToken(String token){
        return extractAllClaims(token).get("role", String.class);
    }
}
