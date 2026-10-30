package br.com.contadinho.cartoes;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Cartão de crédito do dono (HU-05). Do número, só os 4 últimos dígitos (RN-07).
 */
@Entity
class Cartao {

	@Id
	private UUID id;

	private UUID usuarioId;

	private String apelido;

	@Enumerated(EnumType.STRING)
	private Banco banco;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "ultimos4", length = 4)
	private String ultimos4;

	/** RN-08: 1 a 31; em mês mais curto vale o último dia. */
	private short diaFechamento;

	private short diaVencimento;

	private Instant encerradoEm;

	private Instant criadoEm;

	protected Cartao() {
	}

	Cartao(UUID usuarioId, String apelido, Banco banco, String ultimos4, int diaFechamento, int diaVencimento,
			Instant agora) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.apelido = apelido;
		this.banco = banco;
		this.ultimos4 = ultimos4;
		this.diaFechamento = (short) diaFechamento;
		this.diaVencimento = (short) diaVencimento;
		this.criadoEm = agora;
	}

	UUID getId() {
		return id;
	}

	String getApelido() {
		return apelido;
	}

	Banco getBanco() {
		return banco;
	}

	String getUltimos4() {
		return ultimos4;
	}

	int getDiaFechamento() {
		return diaFechamento;
	}

	int getDiaVencimento() {
		return diaVencimento;
	}

}
