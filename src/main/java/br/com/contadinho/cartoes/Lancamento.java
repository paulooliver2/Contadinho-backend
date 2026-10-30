package br.com.contadinho.cartoes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Compra, parcela ou estorno de uma fatura. Vem do arquivo do banco (HU-06) ou é digitada (HU-47).
 */
@Entity
class Lancamento {

	enum Origem {
		ARQUIVO, MANUAL
	}

	@Id
	private UUID id;

	private UUID usuarioId;

	private UUID cartaoId;

	private UUID faturaId;

	/** Só a do arquivo tem importação e id externo (RN-11). */
	private UUID importacaoId;

	private String idExterno;

	@Enumerated(EnumType.STRING)
	private Origem origem;

	private LocalDate data;

	private String descricao;

	private String descricaoNormalizada;

	/** Negativo é estorno (RN-20). */
	private BigDecimal valor;

	private Short parcelaAtual;

	private Short parcelaTotal;

	/** Final do cartão que fez a compra, quando o arquivo traz (RN-19). */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 4)
	private String finalCartao;

	protected Lancamento() {
	}

	static Lancamento digitado(UUID usuarioId, UUID cartaoId, UUID faturaId, Compra compra) {
		var l = new Lancamento();
		l.id = UUID.randomUUID();
		l.usuarioId = usuarioId;
		l.cartaoId = cartaoId;
		l.faturaId = faturaId;
		l.origem = Origem.MANUAL;
		l.corrigir(compra);
		return l;
	}

	static Lancamento doArquivo(UUID usuarioId, UUID cartaoId, UUID faturaId, UUID importacaoId, ArquivoFatura.Item item) {
		var l = new Lancamento();
		l.id = UUID.randomUUID();
		l.usuarioId = usuarioId;
		l.cartaoId = cartaoId;
		l.faturaId = faturaId;
		l.corrigir(new Compra(item.data(), item.descricao(), item.valor(), item.parcelaAtual(), item.parcelaTotal()));
		l.casar(importacaoId, item);
		return l;
	}

	/**
	 * RN-59: a digitada era a mesma compra do arquivo. Passa a ser do arquivo, com a descrição do banco;
	 * responsável, categoria e essencial ficam como estavam.
	 */
	void casar(UUID importacaoId, ArquivoFatura.Item item) {
		this.origem = Origem.ARQUIVO;
		this.importacaoId = importacaoId;
		this.idExterno = item.idExterno();
		this.finalCartao = item.finalCartao();
		this.descricao = item.descricao();
		this.descricaoNormalizada = Descricoes.normalizar(item.descricao());
		if (item.parcelaTotal() != null) {
			this.parcelaAtual = item.parcelaAtual().shortValue();
			this.parcelaTotal = item.parcelaTotal().shortValue();
		}
	}

	/** RN-60: só a digitada pode mudar esses campos. */
	void corrigir(Compra compra) {
		this.data = compra.data();
		this.descricao = compra.descricao();
		this.descricaoNormalizada = Descricoes.normalizar(compra.descricao());
		this.valor = compra.valor();
		this.parcelaAtual = compra.parcelaAtual() == null ? null : compra.parcelaAtual().shortValue();
		this.parcelaTotal = compra.parcelaTotal() == null ? null : compra.parcelaTotal().shortValue();
	}

	/** O que o dono informa ao digitar uma compra (RN-60). */
	record Compra(LocalDate data, String descricao, BigDecimal valor, Integer parcelaAtual, Integer parcelaTotal) {
	}

	UUID getId() {
		return id;
	}

	UUID getFaturaId() {
		return faturaId;
	}

	String getIdExterno() {
		return idExterno;
	}

	String getFinalCartao() {
		return finalCartao;
	}

	Origem getOrigem() {
		return origem;
	}

	LocalDate getData() {
		return data;
	}

	String getDescricao() {
		return descricao;
	}

	BigDecimal getValor() {
		return valor;
	}

	Integer getParcelaAtual() {
		return parcelaAtual == null ? null : parcelaAtual.intValue();
	}

	Integer getParcelaTotal() {
		return parcelaTotal == null ? null : parcelaTotal.intValue();
	}

}
