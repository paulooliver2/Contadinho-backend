package br.com.contadinho.conta.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Link de recuperação de senha (RN-04): vale 30 minutos e uma única vez. Do token só guardamos o hash.
 */
@Entity
public class TokenRecuperacao {

	@Id
	private UUID id;

	private UUID usuarioId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 64)
	private String tokenHash;

	private Instant expiraEm;

	private Instant usadoEm;

	protected TokenRecuperacao() {
	}

	public TokenRecuperacao(UUID usuarioId, String tokenHash, Instant expiraEm) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.tokenHash = tokenHash;
		this.expiraEm = expiraEm;
	}

	public boolean valido(Instant agora) {
		return usadoEm == null && agora.isBefore(expiraEm);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUsuarioId() {
		return usuarioId;
	}

}
