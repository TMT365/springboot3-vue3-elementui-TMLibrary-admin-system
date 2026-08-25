package com.tmt.TMLibrary.security;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.entity.User;
import com.tmt.TMLibrary.exception.AuthException;
import java.util.Date;

/**
 * 介于Filter和Controller层之间的处理服务，判断用户的登录是否正常，不正常不允许进入特定的Controller访问路径。
 * 用于在判断服务器的密钥是否正确，登录正常后签发JWT令牌，下次请求时解析JWT令牌
 * JwtService   
 */
@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    // 必须有 setter(Spring 用 setter 注入配置)
    // public void setJwtProperties(JwtProperties jwtProperties) {
    //     this.jwtProperties = jwtProperties;
    // }


    @PostConstruct
    public void init() {
        // 可以考虑使用Base64做secret,然后进行解码校验长度
        if (this.jwtProperties.getSecret().getBytes().length < 32) {
            throw new IllegalStateException("JWT secret 必须 ≥ 32 字节");
        }


        this.secretKey = Keys.hmacShaKeyFor(this.jwtProperties.getSecret().getBytes());
    }

    public String issue(User user) {
        JwtBuilder jwtBuilder = Jwts.builder();
        jwtBuilder.header().add("akId", "aKeyId");
        jwtBuilder.subject(user.getUsername()).claim("uid", user.getId()).claim("role", user.getRole())
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + this.jwtProperties.getExpirationSeconds() * 1000));
        String token = jwtBuilder.signWith(secretKey).compact();
        return token;
    }

    public Claims parse(String token) {
        JwtParserBuilder jwtParserBuilder = Jwts.parser();
        JwtParser jwtParser = jwtParserBuilder.verifyWith(secretKey).build();
        try {
            return jwtParser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException | SignatureException | MalformedJwtException e) {
            throw new AuthException(ResultCode.UNAUTHORIZED, "令牌无效");
        }
    }
}
