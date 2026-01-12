package org.example.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "security.jwt")
@Getter
public class JwtProperties {

    private String secret;
    private Duration accessTokenExpiration;
    private Duration refreshTokenExpiration;

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setAccessTokenExpiration(Duration accessTokenExpiration) {
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public void setRefreshTokenExpiration(Duration refreshTokenExpiration) {
        this.refreshTokenExpiration = refreshTokenExpiration;
    }
}