package br.com.contadinho.cartoes;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Fatura de um cartão, gravada quando recebe o primeiro lançamento. As datas ficam congeladas: mudar os dias
 * do cartão só vale para as próximas (RN-55).
 */
@Entity
class Fatura {

	@Id
	private UUID id;

	private UUID usuarioId;

	private UUID cartaoId;

	private LocalDate fechamento;

	private LocalDate vencimento;

	protected Fatura() {
	}

	Fatura(UUID usuarioId, UUID cartaoId, Cartao.Ciclo ciclo) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.cartaoId = cartaoId;
		this.fechamento = ciclo.fechamento();
		this.vencimento = ciclo.vencimento();
	}

	UUID getId() {
		return id;
	}

	LocalDate getFechamento() {
		return fechamento;
	}

	LocalDate getVencimento() {
		return vencimento;
	}

}
