package br.com.contadinho.investimentos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Uma mudança na posição (HU-30): a inicial, um aporte ou um resgate. Ações e cripto vão por quantidade e preço;
 * o Tesouro, por quantidade e valor; a renda fixa, só por valor.
 */
@Entity
@Table(name = "movimentacao_investimento")
public class Movimentacao {

	public enum Tipo {
		INICIAL, APORTE, RESGATE
	}

	@Id
	private UUID id;

	private UUID investimentoId;

	private UUID usuarioId;

	@Enumerated(EnumType.STRING)
	private Tipo tipo;

	private LocalDate data;

	private BigDecimal quantidade;

	private BigDecimal preco;

	private BigDecimal valor;

	private boolean total;

	private Instant criadoEm;

	protected Movimentacao() {
	}

	/** Sem valor e com quantidade e preço, o valor é quantidade × preço, em centavos. */
	public Movimentacao(UUID investimentoId, UUID usuarioId, Tipo tipo, LocalDate data, BigDecimal quantidade,
			BigDecimal preco, BigDecimal valor, boolean total, Instant agora) {
		this.id = UUID.randomUUID();
		this.investimentoId = investimentoId;
		this.usuarioId = usuarioId;
		this.tipo = tipo;
		this.data = data;
		this.quantidade = quantidade;
		this.preco = preco;
		this.valor = valor != null ? valor.setScale(2, RoundingMode.HALF_UP)
				: quantidade != null && preco != null
						? quantidade.multiply(preco).setScale(2, RoundingMode.HALF_UP) : null;
		this.total = total;
		this.criadoEm = agora;
	}

	public UUID getId() {
		return id;
	}

	public UUID getInvestimentoId() {
		return investimentoId;
	}

	public Tipo getTipo() {
		return tipo;
	}

	public LocalDate getData() {
		return data;
	}

	public BigDecimal getQuantidade() {
		return quantidade;
	}

	public BigDecimal getPreco() {
		return preco;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public boolean isTotal() {
		return total;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

}
