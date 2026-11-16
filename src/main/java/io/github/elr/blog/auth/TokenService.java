package io.github.elr.blog.auth;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Issues access tokens. A token carries the user's ID as its subject ({@code sub}), when it
 * was issued and when it expires, and who issued it. Nothing else: the user's details are
 * loaded from the database when needed, so a token never holds stale data.
 */
@Service
class TokenService {

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    TokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    AccessToken issue(UUID userId) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer(JwtConfiguration.ISSUER)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.tokenTtl()))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, properties.tokenTtl().toSeconds());
    }
}
