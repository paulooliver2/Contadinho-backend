package br.com.contadinho.cartoes.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import br.com.contadinho.cartoes.importacao.ArquivoFatura;
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
public class Lancamento {

	public enum Origem {
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

	/** Responsável pela compra; null é o dono (RN-15). */
	private UUID pessoaId;

	/** O responsável veio das atribuições anteriores (RN-18), e o dono ainda não tocou nele (HU-09.3). */
	private boolean sugerido;

	/** Digitada e depois casada com a compra do arquivo (RN-59): desfeita a importação, volta a ser digitada (HU-44). */
	private boolean digitadoAntes;

	protected Lancamento() {
	}

	public static Lancamento digitado(UUID usuarioId, UUID cartaoId, UUID faturaId, Compra compra) {
		var l = new Lancamento();
		l.id = UUID.randomUUID();
		l.usuarioId = usuarioId;
		l.cartaoId = cartaoId;
		l.faturaId = faturaId;
		l.origem = Origem.MANUAL;
		l.corrigir(compra);
		return l;
	}

	public static Lancamento doArquivo(UUID usuarioId, UUID cartaoId, UUID faturaId, UUID importacaoId, ArquivoFatura.Item item) {
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
	public void casar(UUID importacaoId, ArquivoFatura.Item item) {
		this.digitadoAntes = this.origem == Origem.MANUAL;
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

	/** HU-44: a importação que a casou foi desfeita; fica a descrição do banco, e o responsável segue o mesmo. */
	public void descasar() {
		this.origem = Origem.MANUAL;
		this.importacaoId = null;
		this.idExterno = null;
		this.finalCartao = null;
		this.digitadoAntes = false;
	}

	/** RN-60: só a digitada pode mudar esses campos. */
	public void corrigir(Compra compra) {
		this.data = compra.data();
		this.descricao = compra.descricao();
		this.descricaoNormalizada = Descricoes.normalizar(compra.descricao());
		this.valor = compra.valor();
		this.parcelaAtual = compra.parcelaAtual() == null ? null : compra.parcelaAtual().shortValue();
		this.parcelaTotal = compra.parcelaTotal() == null ? null : compra.parcelaTotal().shortValue();
	}

	/** HU-08: de quem é a compra; null volta para o dono. Escolhido pelo dono, deixa de ser sugerido (HU-09.3). */
	public void atribuir(UUID pessoaId) {
		this.pessoaId = pessoaId;
		this.sugerido = false;
	}

	/** RN-18: a pessoa de sempre para esta descrição, até o dono conferir. */
	public void sugerir(UUID pessoaId) {
		this.pessoaId = pessoaId;
		this.sugerido = true;
	}

	/** O que o dono informa ao digitar uma compra (RN-60). */
	public record Compra(LocalDate data, String descricao, BigDecimal valor, Integer parcelaAtual, Integer parcelaTotal) {
	}

	public UUID getId() {
		return id;
	}

	public UUID getUsuarioId() {
		return usuarioId;
	}

	public UUID getCartaoId() {
		return cartaoId;
	}

	public UUID getPessoaId() {
		return pessoaId;
	}

	public boolean isSugerido() {
		return sugerido;
	}

	public UUID getFaturaId() {
		return faturaId;
	}

	public UUID getImportacaoId() {
		return importacaoId;
	}

	public boolean isDigitadoAntes() {
		return digitadoAntes;
	}

	public String getIdExterno() {
		return idExterno;
	}

	public String getFinalCartao() {
		return finalCartao;
	}

	public Origem getOrigem() {
		return origem;
	}

	public LocalDate getData() {
		return data;
	}

	public String getDescricao() {
		return descricao;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public Integer getParcelaAtual() {
		return parcelaAtual == null ? null : parcelaAtual.intValue();
	}

	public Integer getParcelaTotal() {
		return parcelaTotal == null ? null : parcelaTotal.intValue();
	}

}
