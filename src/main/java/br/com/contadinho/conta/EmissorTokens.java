package br.com.contadinho.conta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import br.com.contadinho.comum.JwtProperties;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Token de acesso: JWT de 15 minutos com o id do usuário no {@code sub}. Refresh token: 32 bytes aleatórios,
 * opacos para o app; no banco vai só o SHA-256 dele.
 */
@Component
class EmissorTokens {

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private final JwtEncoder encoder;

	private final JwtProperties props;

	EmissorTokens(JwtEncoder encoder, JwtProperties props) {
		this.encoder = encoder;
		this.props = props;
	}

	String acesso(UUID usuarioId, Instant agora) {
		var claims = JwtClaimsSet.builder()
			.issuer("contadinho")
			.subject(usuarioId.toString())
			.issuedAt(agora)
			.expiresAt(acessoExpiraEm(agora))
			.build();
		var cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
	}

	Instant acessoExpiraEm(Instant agora) {
		return agora.plus(props.accessTtl());
	}

	Instant refreshExpiraEm(Instant agora) {
		return agora.plus(props.refreshTtl());
	}

	static String novoRefresh() {
		var bytes = new byte[32];
		ALEATORIO.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String token) {
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
