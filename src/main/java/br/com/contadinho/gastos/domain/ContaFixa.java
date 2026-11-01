package br.com.contadinho.gastos.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

/**
 * Conta que se repete todo mês (HU-11). O valor fica em {@link ContaFixaValor}, porque mudar vale do mês atual em
 * diante (RN-21); cada mês tem uma {@link OcorrenciaConta}.
 */
@Entity
public class ContaFixa {

	public enum FormaPagamento {
		PIX_BOLETO, DEBITO_AUTOMATICO, CARTAO
	}

	@Id
	private UUID id;

	private UUID usuarioId;

	private String descricao;

	private UUID categoriaId;

	private short diaVencimento;

	@Enumerated(EnumType.STRING)
	private FormaPagamento formaPagamento;

	/** Só quando a forma é cartão (RN-22). */
	private UUID cartaoId;

	/** Null é o padrão da categoria (RN-28). */
	private Boolean essencial;

	/** Primeiro dia do mês da primeira ocorrência. */
	private LocalDate inicio;

	/** RN-21: a conta gera até o mês do encerramento e para no seguinte. */
	private LocalDate encerradaEm;

	protected ContaFixa() {
	}

	public ContaFixa(UUID usuarioId, YearMonth primeiroMes) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.inicio = primeiroMes.atDay(1);
	}

	public void alterar(String descricao, UUID categoriaId, int diaVencimento, FormaPagamento formaPagamento,
			UUID cartaoId, Boolean essencial) {
		this.descricao = descricao;
		this.categoriaId = categoriaId;
		this.diaVencimento = (short) diaVencimento;
		this.formaPagamento = formaPagamento;
		this.cartaoId = formaPagamento == FormaPagamento.CARTAO ? cartaoId : null;
		this.essencial = essencial;
	}

	public void encerrar(LocalDate hoje) {
		if (encerradaEm == null) {
			encerradaEm = hoje;
		}
	}

	/** Gera ocorrência no mês: do primeiro mês até o do encerramento. */
	public boolean ativaEm(YearMonth mes) {
		return !mes.isBefore(YearMonth.from(inicio)) && (encerradaEm == null || !mes.isAfter(YearMonth.from(encerradaEm)));
	}

	/** O dia de vencimento no mês; dia 31 em fevereiro vira o último dia (28 ou 29). */
	public LocalDate vencimentoEm(YearMonth mes) {
		return mes.atDay(Math.min(diaVencimento, mes.lengthOfMonth()));
	}

	public boolean isNoCartao() {
		return formaPagamento == FormaPagamento.CARTAO;
	}

	public YearMonth getPrimeiroMes() {
		return YearMonth.from(inicio);
	}

	public boolean isEncerrada() {
		return encerradaEm != null;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUsuarioId() {
		return usuarioId;
	}

	public String getDescricao() {
		return descricao;
	}

	public UUID getCategoriaId() {
		return categoriaId;
	}

	public short getDiaVencimento() {
		return diaVencimento;
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

	public LocalDate getEncerradaEm() {
		return encerradaEm;
	}

}
