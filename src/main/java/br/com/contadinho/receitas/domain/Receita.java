package br.com.contadinho.receitas.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * O que entra (HU-22): uma vez, num dia, ou todo mês, a partir de um mês. Cada mês tem uma {@link OcorrenciaReceita},
 * que o dono confirma quando cai.
 */
@Entity
public class Receita {

	@Id
	private UUID id;

	private UUID usuarioId;

	private String descricao;

	private BigDecimal valor;

	private boolean recorrente;

	private short dia;

	/** Primeiro dia do mês: o único, se for uma vez; o primeiro, se for todo mês. */
	private LocalDate inicio;

	/** A recorrente gera até o mês do encerramento e para no seguinte. */
	private LocalDate encerradaEm;

	protected Receita() {
	}

	public Receita(UUID usuarioId, boolean recorrente) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.recorrente = recorrente;
	}

	/** Uma vez: a data é a da receita. Todo mês: vale o dia; o mês de início só muda antes de ela começar. */
	public void alterar(String descricao, BigDecimal valor, LocalDate data, YearMonth mesAtual) {
		this.descricao = descricao;
		this.valor = valor.setScale(2);
		this.dia = (short) data.getDayOfMonth();
		if (!recorrente || inicio == null || mesAtual.isBefore(getPrimeiroMes())) {
			this.inicio = YearMonth.from(data).atDay(1);
		}
	}

	public void encerrar(LocalDate hoje) {
		if (encerradaEm == null) {
			encerradaEm = hoje;
		}
	}

	public boolean ativaEm(YearMonth mes) {
		if (!recorrente) {
			return mes.equals(getPrimeiroMes());
		}
		return !mes.isBefore(getPrimeiroMes()) && (encerradaEm == null || !mes.isAfter(YearMonth.from(encerradaEm)));
	}

	/** O dia no mês; dia 31 em fevereiro vira o último dia. */
	public LocalDate dataEm(YearMonth mes) {
		return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
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

	public boolean isRecorrente() {
		return recorrente;
	}

	public int getDia() {
		return dia;
	}

	public YearMonth getPrimeiroMes() {
		return YearMonth.from(inicio);
	}

	public LocalDate getEncerradaEm() {
		return encerradaEm;
	}

}
