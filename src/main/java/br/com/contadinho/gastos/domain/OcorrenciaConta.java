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

	/** RN-24: o dono desfez o pagamento automático do débito; o app não marca de novo. */
	private boolean automaticoDesfeito;

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

	/** HU-13.1. O valor pago pode diferir do previsto (RN-25). */
	public void pagar(LocalDate em, BigDecimal valor) {
		this.pagoEm = em;
		this.valorPago = valor;
	}

	/** HU-13.3: desfeito, o débito automático não volta a ser marcado sozinho. */
	public void desfazerPagamento(boolean debitoAutomatico) {
		this.pagoEm = null;
		this.valorPago = null;
		if (debitoAutomatico) {
			this.automaticoDesfeito = true;
		}
	}

	/** RN-24: débito automático em aberto, já vencido e que o dono não desfez. */
	public boolean debitaSozinha(LocalDate hoje) {
		return emAberto() && !automaticoDesfeito && !vencimento.isAfter(hoje);
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
