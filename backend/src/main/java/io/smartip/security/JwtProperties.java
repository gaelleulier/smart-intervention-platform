package io.smartip.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("smart-intervention-platform") String issuer,
        @DefaultValue("PT1H") Duration expiration) {}
