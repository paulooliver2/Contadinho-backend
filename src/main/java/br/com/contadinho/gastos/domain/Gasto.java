package br.com.contadinho.gastos.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

/**
 * Gasto único, pago fora do cartão (HU-12). Se for no cartão, só aguarda o lançamento da fatura e nunca soma no total
 * do mês (RN-26).
 */
@Entity
public class Gasto {

	public enum FormaPagamento {
		PIX_BOLETO, DEBITO, CARTAO
	}

	/** Fora do cartão está pago; no cartão aguarda a fatura ou já está nela (RN-26). */
	public enum Status {
		PAGO, AGUARDANDO_FATURA, NA_FATURA
	}

	@Id
	private UUID id;

	private UUID usuarioId;

	private String descricao;

	private BigDecimal valor;

	private LocalDate data;

	private UUID categoriaId;

	@Enumerated(EnumType.STRING)
	private FormaPagamento formaPagamento;

	/** Só quando a forma é cartão. */
	private UUID cartaoId;

	/** Null é o padrão da categoria (RN-28). */
	private Boolean essencial;

	/** RN-26: o lançamento da fatura em que o gasto no cartão caiu. */
	private UUID lancamentoId;

	protected Gasto() {
	}

	public Gasto(UUID usuarioId) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
	}

	/** HU-46.1. Mudar a forma ou o cartão desfaz a ligação com a fatura. */
	public void alterar(String descricao, BigDecimal valor, LocalDate data, UUID categoriaId,
			FormaPagamento formaPagamento, UUID cartaoId, Boolean essencial) {
		this.descricao = descricao;
		this.valor = valor.setScale(2);
		this.data = data;
		this.categoriaId = categoriaId;
		this.formaPagamento = formaPagamento;
		this.cartaoId = formaPagamento == FormaPagamento.CARTAO ? cartaoId : null;
		this.essencial = essencial;
		this.lancamentoId = null;
	}

	public void ligar(UUID lancamentoId) {
		this.lancamentoId = lancamentoId;
	}

	public Status status() {
		if (!isNoCartao()) {
			return Status.PAGO;
		}
		return lancamentoId == null ? Status.AGUARDANDO_FATURA : Status.NA_FATURA;
	}

	public boolean isNoCartao() {
		return formaPagamento == FormaPagamento.CARTAO;
	}

	public UUID getId() {
		return id;
	}

	public String getDescricao() {
		return descricao;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public LocalDate getData() {
		return data;
	}

	public UUID getCategoriaId() {
		return categoriaId;
	}

	public FormaPagamento getFormaPagamento() {
		return formaPagamento;
	}

	public UUID getCartaoId() {
		return cartaoId;
	}

	public Boolean getEssencial() {
		return essencial;
	}

	public UUID getLancamentoId() {
		return lancamentoId;
	}

}
