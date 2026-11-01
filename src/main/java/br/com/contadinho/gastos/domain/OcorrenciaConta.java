package br.com.contadinho.gastos.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A conta fixa num mês (HU-11.2). O status não é guardado: sai do pagamento ou da ligação com a fatura (RN-57). */
@Entity
@Table(name = "ocorrencia_conta")
public class OcorrenciaConta {

	public enum Status {
		A_PAGAR, PAGA, AGUARDANDO_FATURA, NA_FATURA
	}

	@Id
	private UUID id;

	private UUID usuarioId;

	private UUID contaFixaId;

	/** Primeiro dia do mês. */
	private LocalDate competencia;

	private LocalDate vencimento;

	private BigDecimal valorPrevisto;

	/** RN-25: quando difere do previsto, o total do mês usa este. */
	private BigDecimal valorPago;

	private LocalDate pagoEm;

	/** RN-23: o lançamento da fatura em que a conta paga no cartão caiu. */
	private UUID lancamentoId;

	protected OcorrenciaConta() {
	}

	/** RN-57. */
	public Status status(boolean noCartao) {
		if (noCartao) {
			return lancamentoId == null ? Status.AGUARDANDO_FATURA : Status.NA_FATURA;
		}
		return pagoEm == null ? Status.A_PAGAR : Status.PAGA;
	}

	/** Ainda acompanha o cadastro: nem paga nem ligada a um lançamento. */
	public boolean emAberto() {
		return pagoEm == null && lancamentoId == null;
	}

	/** HU-11.3: o valor ou o dia mudou do mês atual em diante. */
	public void acompanhar(BigDecimal valor, LocalDate vencimento) {
		this.valorPrevisto = valor;
		this.vencimento = vencimento;
	}

	/** O que conta no mês: o valor pago, se houver; senão, o previsto (RN-25). */
	public BigDecimal valor() {
		return valorPago != null ? valorPago : valorPrevisto;
	}

	public UUID getId() {
		return id;
	}

	public UUID getContaFixaId() {
		return contaFixaId;
	}

	public YearMonth getMes() {
		return YearMonth.from(competencia);
	}

	public LocalDate getVencimento() {
		return vencimento;
	}

	public BigDecimal getValorPrevisto() {
		return valorPrevisto;
	}

	public BigDecimal getValorPago() {
		return valorPago;
	}

	public LocalDate getPagoEm() {
		return pagoEm;
	}

}
