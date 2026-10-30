package br.com.contadinho.conta;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Uma sessão por aparelho, identificada pelo refresh token (HU-01.3). Do token só guardamos o hash.
 */
@Entity
class Sessao {

	@Id
	private UUID id;

	private UUID usuarioId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 64)
	private String tokenHash;

	private String dispositivo;

	private Instant criadaEm;

	private Instant expiraEm;

	private Instant revogadaEm;

	protected Sessao() {
	}

	Sessao(UUID usuarioId, String tokenHash, String dispositivo, Instant agora, Instant expiraEm) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.tokenHash = tokenHash;
		this.dispositivo = dispositivo;
		this.criadaEm = agora;
		this.expiraEm = expiraEm;
	}

	boolean ativa(Instant agora) {
		return revogadaEm == null && agora.isBefore(expiraEm);
	}

	UUID getId() {
		return id;
	}

	UUID getUsuarioId() {
		return usuarioId;
	}

	String getDispositivo() {
		return dispositivo;
	}

}
