package br.com.contadinho.investimentos.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

/**
 * A última cotação de um ativo (HU-31), compartilhada entre donos. Gravada por upsert no
 * {@code AtualizadorCotacoes}; aqui só se lê.
 */
@Entity
public class Cotacao {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	private Investimento.Tipo tipo;

	private String codigo;

	private BigDecimal preco;

	private LocalDate referencia;

	private Instant atualizadoEm;

	private Instant falhouEm;

	private String fonte;

	protected Cotacao() {
	}

	public UUID getId() {
		return id;
	}

	public Investimento.Tipo getTipo() {
		return tipo;
	}

	public String getCodigo() {
		return codigo;
	}

	/** Nulo até a primeira busca que der certo. */
	public BigDecimal getPreco() {
		return preco;
	}

	/** O dia a que o preço se refere (no Tesouro, a data base da planilha). */
	public LocalDate getReferencia() {
		return referencia;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

	public Instant getFalhouEm() {
		return falhouEm;
	}

	public String getFonte() {
		return fonte;
	}

}
