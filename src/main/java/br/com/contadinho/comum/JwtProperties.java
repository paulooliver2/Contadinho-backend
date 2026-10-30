package br.com.contadinho.comum;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tokens da HU-01: acesso de 15 minutos e refresh de 30 dias.
 */
@ConfigurationProperties("contadinho.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {

	public JwtProperties {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("contadinho.jwt.secret precisa ter pelo menos 32 bytes (defina JWT_SECRET)");
		}
	}

}
