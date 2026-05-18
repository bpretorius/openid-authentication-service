package com.openbanking.authentication.config;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

public class CustomJwtEncoder implements JwtEncoder {

    private final NimbusJwtEncoder delegate;

    public CustomJwtEncoder(JWKSource<SecurityContext> jwkSource) {
        this.delegate = new NimbusJwtEncoder(jwkSource);
    }

    @Override
    public Jwt encode(JwtEncoderParameters parameters) throws JwtEncodingException {
        JwsHeader headers = JwsHeader.with(SignatureAlgorithm.RS256)
                .type("JWT")  // Set the typ header
                .build();

        return delegate.encode(JwtEncoderParameters.from(headers, parameters.getClaims()));
    }
}

