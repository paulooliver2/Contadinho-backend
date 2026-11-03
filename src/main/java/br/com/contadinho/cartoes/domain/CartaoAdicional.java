package br.com.contadinho.cartoes.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Cartão adicional: as compras com este final chegam atribuídas à pessoa (HU-38, RN-19). */
@Entity
public class CartaoAdicional {

	@Id
	private UUID id;

	private UUID usuarioId;

	private UUID cartaoId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "ultimos4", length = 4)
	private String ultimos4;

	private UUID pessoaId;

	protected CartaoAdicional() {
	}

	public CartaoAdicional(UUID usuarioId, UUID cartaoId, String ultimos4, UUID pessoaId) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.cartaoId = cartaoId;
		this.ultimos4 = ultimos4;
		this.pessoaId = pessoaId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getCartaoId() {
		return cartaoId;
	}

	public String getUltimos4() {
		return ultimos4;
	}

	public UUID getPessoaId() {
		return pessoaId;
	}

}
