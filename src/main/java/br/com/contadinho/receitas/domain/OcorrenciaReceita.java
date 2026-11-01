package br.com.contadinho.receitas.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A receita num mês (HU-22.2). O status não é guardado: sai da confirmação e da data. */
@Entity
@Table(name = "ocorrencia_receita")
public class OcorrenciaReceita {

	public enum Status {
		/** Ainda não chegou o dia. */
		PREVISTA,
		/** O dia chegou e o dono não confirmou. */
		A_CONFIRMAR,
		RECEBIDA
	}

	@Id
	private UUID id;

	private UUID usuarioId;

	private UUID receitaId;

	/** Primeiro dia do mês. */
	private LocalDate competencia;

	private LocalDate data;

	private BigDecimal valorPrevisto;

	/** HU-22.2: confirmado, talvez com outro valor. */
	private BigDecimal valorRecebido;

	protected OcorrenciaReceita() {
	}

	public Status status(LocalDate hoje) {
		if (valorRecebido != null) {
			return Status.RECEBIDA;
		}
		return data.isAfter(hoje) ? Status.PREVISTA : Status.A_CONFIRMAR;
	}

	public void confirmar(BigDecimal valor) {
		this.valorRecebido = valor.setScale(2);
	}

	public void desfazer() {
		this.valorRecebido = null;
	}

	public boolean emAberto() {
		return valorRecebido == null;
	}

	/** O cadastro mudou: a do mês acompanha. */
	public void acompanhar(BigDecimal valor, LocalDate data) {
		this.valorPrevisto = valor;
		this.data = data;
		this.competencia = YearMonth.from(data).atDay(1);
	}

	/** O que conta no mês: o recebido, se confirmado; senão, o previsto. */
	public BigDecimal valor() {
		return valorRecebido != null ? valorRecebido : valorPrevisto;
	}

	public UUID getId() {
		return id;
	}

	public UUID getReceitaId() {
		return receitaId;
	}

	public YearMonth getMes() {
		return YearMonth.from(competencia);
	}

	public LocalDate getData() {
		return data;
	}

	public BigDecimal getValorPrevisto() {
		return valorPrevisto;
	}

	public BigDecimal getValorRecebido() {
		return valorRecebido;
	}

}
