package com.cloudflow.service;

import java.time.Clock;
import java.time.Instant;

import com.cloudflow.dto.response.LoginResponse;
import com.cloudflow.entity.User;
import com.cloudflow.security.SecurityProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final SecurityProperties properties;

    public AccessTokenService(JwtEncoder encoder, Clock clock, SecurityProperties properties) {
        this.encoder = encoder;
        this.clock = clock;
        this.properties = properties;
    }

    public LoginResponse issue(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.jwt().expiration());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cloudflow")
                .subject(user.getPublicId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, properties.jwt().expiration().toSeconds(), "Bearer");
    }
}
