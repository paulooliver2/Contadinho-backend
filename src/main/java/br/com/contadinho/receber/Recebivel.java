package br.com.contadinho.receber;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Um valor que uma pessoa deve ao dono: uma compra da fatura atribuída a ela (RN-16) ou, depois, uma parcela de
 * empréstimo (RN-31).
 */
@Entity
class Recebivel {

	@Id
	private UUID id;

	private UUID usuarioId;

	private UUID pessoaId;

	/** A compra da fatura; apagar a compra apaga o recebível (RN-54). */
	private UUID lancamentoId;

	private UUID emprestimoId;

	private Short parcela;

	/** Negativo quando é estorno (RN-20). */
	private BigDecimal valor;

	/** O da fatura, para compras; null é "Sem data" (RN-32). */
	private LocalDate vencimento;

	protected Recebivel() {
	}

	static Recebivel daCompra(UUID usuarioId, UUID lancamentoId) {
		var r = new Recebivel();
		r.id = UUID.randomUUID();
		r.usuarioId = usuarioId;
		r.lancamentoId = lancamentoId;
		return r;
	}

	/** RN-16: trocar o responsável move o recebível; o valor e o vencimento seguem a compra. */
	void acompanhar(UUID pessoaId, BigDecimal valor, LocalDate vencimento) {
		this.pessoaId = pessoaId;
		this.valor = valor;
		this.vencimento = vencimento;
	}

	UUID getPessoaId() {
		return pessoaId;
	}

	UUID getLancamentoId() {
		return lancamentoId;
	}

	BigDecimal getValor() {
		return valor;
	}

	LocalDate getVencimento() {
		return vencimento;
	}

}
