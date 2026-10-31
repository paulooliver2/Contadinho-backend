package br.com.contadinho.cartoes.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * RN-17: o responsável das próximas parcelas de uma compra, aplicado quando cada parcela chega numa fatura.
 * A compra é reconhecida pelo cartão, pela descrição sem a parcela, pelo valor e pelo número de parcelas.
 */
@Entity
public class RegraParcela {

	/** Diferença de centavos entre parcelas da mesma compra (a última costuma levar o resto, RN-40). */
	private static final BigDecimal CENTAVOS = new BigDecimal("0.05");

	@Id
	private UUID id;

	private UUID usuarioId;

	private UUID cartaoId;

	/** {@link Descricoes#daCompra}: sem o "Parcela 3/10". */
	private String descricaoNormalizada;

	private BigDecimal valor;

	private Short parcelaTotal;

	/** Null é o dono (RN-15). */
	private UUID pessoaId;

	private Short aPartirDaParcela;

	protected RegraParcela() {
	}

	/** A partir da parcela seguinte à de {@code parcela}, as parcelas da mesma compra são de {@code pessoaId}. */
	public RegraParcela(Lancamento parcela, UUID pessoaId) {
		this.id = UUID.randomUUID();
		this.usuarioId = parcela.getUsuarioId();
		this.cartaoId = parcela.getCartaoId();
		this.descricaoNormalizada = Descricoes.daCompra(parcela.getDescricao());
		this.valor = parcela.getValor();
		this.parcelaTotal = parcela.getParcelaTotal().shortValue();
		atualizar(parcela, pessoaId);
	}

	public void atualizar(Lancamento parcela, UUID pessoaId) {
		this.pessoaId = pessoaId;
		this.aPartirDaParcela = (short) (parcela.getParcelaAtual() + 1);
	}

	/** É uma parcela da mesma compra, de qualquer mês. */
	public boolean mesmaCompra(Lancamento l) {
		return l.getParcelaTotal() != null && l.getCartaoId().equals(cartaoId)
				&& l.getParcelaTotal() == parcelaTotal.intValue()
				&& l.getValor().subtract(valor).abs().compareTo(CENTAVOS) <= 0
				&& Descricoes.daCompra(l.getDescricao()).equals(descricaoNormalizada);
	}

	/** É uma parcela da mesma compra que a regra cobre. */
	public boolean cobre(Lancamento l) {
		return mesmaCompra(l) && l.getParcelaAtual() >= aPartirDaParcela;
	}

	public UUID getPessoaId() {
		return pessoaId;
	}

}
