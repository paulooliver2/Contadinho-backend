package br.com.contadinho.cartoes.domain;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Fatura de um cartão, gravada quando recebe o primeiro lançamento. As datas ficam congeladas: mudar os dias
 * do cartão só vale para as próximas (RN-55).
 */
@Entity
public class Fatura {

	@Id
	private UUID id;

	private UUID usuarioId;

	private UUID cartaoId;

	private LocalDate fechamento;

	private LocalDate vencimento;

	protected Fatura() {
	}

	public Fatura(UUID usuarioId, UUID cartaoId, Cartao.Ciclo ciclo) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.cartaoId = cartaoId;
		this.fechamento = ciclo.fechamento();
		this.vencimento = ciclo.vencimento();
	}

	public UUID getId() {
		return id;
	}

	public LocalDate getFechamento() {
		return fechamento;
	}

	public LocalDate getVencimento() {
		return vencimento;
	}

}
