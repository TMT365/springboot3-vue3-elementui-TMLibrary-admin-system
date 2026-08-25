package com.tmt.TMLibrary.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.RequiredArgsConstructor;
import lombok.Getter;

@ConfigurationProperties(prefix = "jwt")
@RequiredArgsConstructor
@Getter
public class JwtProperties {
    private final String secret;
    private final Long expirationSeconds;
}
