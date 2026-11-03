package br.com.contadinho.investimentos.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;

/**
 * O valor de hoje (HU-31), calculado na leitura (RNF-05): quantidade × cotação, ou a renda fixa corrigida dia útil a
 * dia útil. O ganho é contra o aplicado da {@link Posicao}.
 */
public final class ValorAtual {

	/**
	 * @param valorAtual nulo quando não há cotação nem CDI depois da aplicação
	 * @param ganhoPercentual nulo sem valor ou com aplicado 0
	 * @param cdiAte só na renda fixa: o último dia útil corrigido
	 */
	public record Resultado(BigDecimal valorAtual, BigDecimal ganho, BigDecimal ganhoPercentual, LocalDate cdiAte) {

		public static final Resultado VAZIO = new Resultado(null, null, null, null);

		static Resultado de(BigDecimal valor, BigDecimal aplicado, LocalDate cdiAte) {
			var atual = valor.setScale(2, RoundingMode.HALF_UP);
			var ganho = atual.subtract(aplicado);
			var percentual = aplicado.signum() == 0 ? null
					: ganho.multiply(BigDecimal.valueOf(100)).divide(aplicado, 2, RoundingMode.HALF_UP);
			return new Resultado(atual, ganho, percentual, cdiAte);
		}
	}

	private static final BigDecimal CEM = BigDecimal.valueOf(100);

	private static final Comparator<Movimentacao> ORDEM = Comparator.comparing(Movimentacao::getData)
		.thenComparing(Movimentacao::getCriadoEm);

	private ValorAtual() {
	}

	/** Ação, FII, cripto e Tesouro: quantidade × preço. Sem preço, ou encerrada, não há valor. */
	public static Resultado mercado(Posicao posicao, BigDecimal preco) {
		if (preco == null || posicao.encerrada() || posicao.quantidade() == null) {
			return Resultado.VAZIO;
		}
		return Resultado.de(posicao.quantidade().multiply(preco), posicao.aplicado(), null);
	}

	/**
	 * CDB, LCI e LCA: o saldo corrigido em cada dia útil do {@code cdi} depois da aplicação, até o último dia guardado
	 * ou o vencimento. No dia de um resgate, primeiro o fator, depois sai o valor (nunca abaixo de 0).
	 * @param cdi o CDI de cada dia útil, em % ao dia; as chaves são o calendário
	 */
	public static Resultado rendaFixa(Investimento investimento, List<Movimentacao> movimentacoes,
			NavigableMap<LocalDate, BigDecimal> cdi) {
		var ordem = new ArrayDeque<>(movimentacoes.stream().sorted(ORDEM).toList());
		var inicial = ordem.pollFirst();
		if (inicial == null || cdi.isEmpty()) {
			return Resultado.VAZIO;
		}
		var limite = investimento.getVencimento() == null || cdi.lastKey().isBefore(investimento.getVencimento())
				? cdi.lastKey() : investimento.getVencimento();
		if (!limite.isAfter(inicial.getData())) {
			return Resultado.VAZIO;
		}
		var dias = cdi.subMap(inicial.getData(), false, limite, true);
		if (dias.isEmpty()) {
			return Resultado.VAZIO;
		}
		var prefixado = investimento.getIndexador() == Investimento.Indexador.PREFIXADO
				? BigDecimal.valueOf(Math.pow(1 + investimento.getTaxa().doubleValue() / 100, 1.0 / 252)) : null;
		var saldo = inicial.getValor();
		LocalDate ultimo = null;
		for (var dia : dias.entrySet()) {
			saldo = resgatar(saldo, ordem, m -> m.getData().isBefore(dia.getKey()));
			var fator = prefixado != null ? prefixado
					: BigDecimal.ONE.add(dia.getValue().divide(CEM, MathContext.DECIMAL128)
						.multiply(investimento.getTaxa().divide(CEM, MathContext.DECIMAL128)));
			saldo = saldo.multiply(fator, MathContext.DECIMAL128);
			saldo = resgatar(saldo, ordem, m -> !m.getData().isAfter(dia.getKey()));
			ultimo = dia.getKey();
		}
		saldo = resgatar(saldo, ordem, m -> true);
		var aplicado = Posicao.de(Investimento.Tipo.RENDA_FIXA, movimentacoes).aplicado();
		return Resultado.de(saldo, aplicado, ultimo);
	}

	/** Renda fixa encerrada: o que voltou menos o que foi aplicado no início. */
	public static BigDecimal resultadoEncerrada(List<Movimentacao> movimentacoes) {
		var resultado = BigDecimal.ZERO;
		for (var m : movimentacoes) {
			resultado = m.getTipo() == Movimentacao.Tipo.RESGATE ? resultado.add(m.getValor())
					: resultado.subtract(m.getValor());
		}
		return resultado.setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal resgatar(BigDecimal saldo, ArrayDeque<Movimentacao> pendentes,
			java.util.function.Predicate<Movimentacao> agora) {
		while (!pendentes.isEmpty() && agora.test(pendentes.peekFirst())) {
			var m = pendentes.pollFirst();
			if (m.getTipo() == Movimentacao.Tipo.RESGATE) {
				saldo = saldo.subtract(m.getValor()).max(BigDecimal.ZERO);
			}
		}
		return saldo;
	}

}
