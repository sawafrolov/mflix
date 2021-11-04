package com.github.sawafrolov.mflix.api.services;

import com.github.sawafrolov.mflix.api.models.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Base64;

import static java.util.Collections.emptyList;

@Service
@Configuration
public class TokenAuthenticationService {

    private final String TOKEN_PREFIX = "Bearer";
    private final String HEADER_STRING = "Authorization";
    private final Logger log;
    @Value("${jwtExpirationInMs}")
    private long jwtExpirationInMs;
    @Value("${jwtSecret}")
    private String jwtSecret;

    public TokenAuthenticationService() {
        super();
        log = LoggerFactory.getLogger(this.getClass());
    }

    public String mintToken(String username) {
        return Base64.getEncoder().encodeToString(username.getBytes());
    }

    public void addAuthentication(HttpServletResponse res, String username) {
        String headerValue = mintToken(username);
        res.addHeader(HEADER_STRING, headerValue);
    }

    private String trimToken(String token) {
        return token.replace(TOKEN_PREFIX, "").trim();
    }

    public String getAuthenticationUser(String token) {
        try {
            return new String(Base64.getDecoder().decode(token));
        } catch (Exception e) {
            log.error("Cannot validate user token `{}`: error thrown - {}", token, e.getMessage());
        }
        return null;
    }

    public Authentication getAuthentication(HttpServletRequest request) {
        String token = request.getHeader(HEADER_STRING);
        if (token != null) {
            // parse the token.
            String user = getAuthenticationUser(token);
            return user != null ? new UsernamePasswordAuthenticationToken(user, null, emptyList()) : null;
        }
        return null;
    }

    public String generateToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return mintToken(userPrincipal.getEmail());
    }
}
