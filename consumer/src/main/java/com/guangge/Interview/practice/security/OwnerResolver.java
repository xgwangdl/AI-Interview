package com.guangge.Interview.practice.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.guangge.Interview.auth.Sign;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the authenticated user name from the bearer token used by the practice API. */
@Component
public class OwnerResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final String tokenSecret;

    public OwnerResolver(@Value("${auth.token.secret}") String tokenSecret) {
        this.tokenSecret = tokenSecret;
    }

    public String requireOwner(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            DecodedJWT token = Sign.verifyToken(authorization.substring(BEARER_PREFIX.length()), tokenSecret);
            String username = token.getClaim("userName").asString();
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Missing user name claim");
            }
            return username;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }
}
