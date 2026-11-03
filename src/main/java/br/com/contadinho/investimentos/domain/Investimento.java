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
 * Uma posição da carteira (HU-30): o que identifica o ativo. Quantidade, preço médio e aplicado vêm das
 * {@link Movimentacao movimentações}, pela {@link Posicao}.
 */
@Entity
public class Investimento {

	public enum Tipo {
		ACAO, CRIPTO, TESOURO, RENDA_FIXA
	}

	public enum Produto {
		CDB, LCI, LCA
	}

	public enum Indexador {
		CDI, PREFIXADO
	}

	@Id
	private UUID id;

	private UUID usuarioId;

	@Enumerated(EnumType.STRING)
	private Tipo tipo;

	private String codigo;

	private String nome;

	private String simbolo;

	@Enumerated(EnumType.STRING)
	private Produto produto;

	@Enumerated(EnumType.STRING)
	private Indexador indexador;

	private BigDecimal taxa;

	private LocalDate vencimento;

	private boolean conferido;

	private Instant criadoEm;

	protected Investimento() {
	}

	/** Ação, cripto ou Tesouro. */
	public Investimento(UUID usuarioId, Tipo tipo, String codigo, String nome, String simbolo, boolean conferido,
			Instant agora) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.tipo = tipo;
		this.codigo = codigo;
		this.nome = nome;
		this.simbolo = simbolo;
		this.conferido = conferido;
		this.criadoEm = agora;
	}

	/** CDB, LCI ou LCA: o emissor é o código e o nome. */
	public static Investimento rendaFixa(UUID usuarioId, Produto produto, String emissor, Indexador indexador,
			BigDecimal taxa, LocalDate vencimento, Instant agora) {
		var i = new Investimento(usuarioId, Tipo.RENDA_FIXA, emissor, emissor, null, true, agora);
		i.produto = produto;
		i.indexador = indexador;
		i.taxa = taxa;
		i.vencimento = vencimento;
		return i;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUsuarioId() {
		return usuarioId;
	}

	public Tipo getTipo() {
		return tipo;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getNome() {
		return nome;
	}

	public String getSimbolo() {
		return simbolo;
	}

	public Produto getProduto() {
		return produto;
	}

	public Indexador getIndexador() {
		return indexador;
	}

	public BigDecimal getTaxa() {
		return taxa;
	}

	public LocalDate getVencimento() {
		return vencimento;
	}

	public boolean isConferido() {
		return conferido;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

}
