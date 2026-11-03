package br.com.contadinho.investimentos.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * HU-32: o valor da carteira em cada dia do período, calculado na leitura (RNF-05) a partir das movimentações, dos
 * preços do dia guardados e do CDI guardado. O ganho do período desconta aportes e resgates (RN-49).
 */
public final class Evolucao {

	public enum Periodo {

		DIA, MES, SEIS_MESES, DOZE_MESES;

		/** O primeiro dia do período, sem olhar quando a carteira começou. */
		LocalDate inicio(LocalDate hoje) {
			return switch (this) {
				case DIA -> hoje;
				case MES -> hoje.minusMonths(1).plusDays(1);
				case SEIS_MESES -> hoje.minusMonths(6).plusDays(1);
				case DOZE_MESES -> hoje.minusMonths(12).plusDays(1);
			};
		}

	}

	/**
	 * @param precos os preços do dia guardados (vazio na renda fixa)
	 * @param precoHoje a cotação atual, a mesma do cartão Patrimônio (nula na renda fixa ou sem cotação)
	 */
	public record Ativo(Investimento investimento, List<Movimentacao> movimentacoes,
			NavigableMap<LocalDate, BigDecimal> precos, BigDecimal precoHoje) {
	}

	public record Ponto(LocalDate data, BigDecimal valor) {
	}

	/**
	 * @param desdeCadastro a carteira é mais nova que o período: ele começa na primeira posição
	 * @param ganhoPercentual nulo quando não havia nada no início nem entrou dinheiro
	 * @param cdiPercentual o CDI acumulado nos mesmos dias; nulo sem CDI guardado
	 * @param cdiAte o último dia de CDI usado
	 */
	public record Resultado(LocalDate de, LocalDate ate, boolean desdeCadastro, List<Ponto> pontos,
			BigDecimal valorInicial, BigDecimal valorFinal, BigDecimal aportes, BigDecimal resgates, BigDecimal ganho,
			BigDecimal ganhoPercentual, BigDecimal cdiPercentual, LocalDate cdiAte) {
	}

	private static final BigDecimal CEM = BigDecimal.valueOf(100);

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private Evolucao() {
	}

	public static Resultado calcular(Periodo periodo, LocalDate hoje, List<Ativo> ativos,
			NavigableMap<LocalDate, BigDecimal> cdi) {
		var comValor = ativos.stream().filter(a -> temValor(a, cdi)).toList();
		// A renda fixa sai de uma passada só pelo CDI, e não de um cálculo inteiro por dia.
		var saldos = new java.util.IdentityHashMap<Ativo, NavigableMap<LocalDate, BigDecimal>>();
		comValor.stream()
			.filter(a -> a.investimento().getTipo() == Investimento.Tipo.RENDA_FIXA)
			.forEach(a -> saldos.put(a, ValorAtual.saldosPorDia(a.investimento(), a.movimentacoes(), cdi, hoje)));
		var nominal = periodo.inicio(hoje);
		var ini = nominal.minusDays(1);
		var primeira = comValor.stream()
			.flatMap(a -> a.movimentacoes().stream())
			.map(Movimentacao::getData)
			.min(LocalDate::compareTo)
			.orElse(null);
		var desdeCadastro = primeira != null && primeira.isAfter(ini);
		var de = desdeCadastro ? primeira : nominal;

		var pontos = new ArrayList<Ponto>();
		if (primeira != null) {
			for (var dia = desdeCadastro ? de : ini; !dia.isAfter(hoje); dia = dia.plusDays(1)) {
				pontos.add(new Ponto(dia, total(comValor, dia, hoje, saldos)));
			}
		}
		var valorInicial = desdeCadastro || primeira == null ? ZERO : pontos.getFirst().valor();
		var valorFinal = pontos.isEmpty() ? ZERO : pontos.getLast().valor();

		var aportes = BigDecimal.ZERO;
		var resgates = BigDecimal.ZERO;
		for (var a : comValor) {
			for (var m : a.movimentacoes()) {
				if (!m.getData().isAfter(ini) || m.getData().isAfter(hoje)) {
					continue;
				}
				var reais = emReais(a, m, hoje);
				if (m.getTipo() == Movimentacao.Tipo.RESGATE) {
					resgates = resgates.add(reais);
				}
				else {
					aportes = aportes.add(reais);
				}
			}
		}
		aportes = aportes.setScale(2, RoundingMode.HALF_UP);
		resgates = resgates.setScale(2, RoundingMode.HALF_UP);
		var ganho = valorFinal.subtract(valorInicial).subtract(aportes).add(resgates);
		var base = valorInicial.add(aportes);
		var ganhoPercentual = base.signum() == 0 ? null
				: ganho.multiply(CEM).divide(base, 2, RoundingMode.HALF_UP);

		// Os mesmos dias do ganho: com a carteira mais nova que o período, desde o primeiro cadastro.
		var diasCdi = cdi.subMap(desdeCadastro ? de : ini, false, hoje, true);
		if (diasCdi.isEmpty() && periodo == Periodo.DIA && cdi.floorKey(hoje) != null) {
			// O CDI de hoje só sai amanhã: o Dia compara com o último dia útil guardado.
			diasCdi = cdi.subMap(cdi.floorKey(hoje), true, cdi.floorKey(hoje), true);
		}
		BigDecimal cdiPercentual = null;
		LocalDate cdiAte = null;
		if (!diasCdi.isEmpty()) {
			var fator = BigDecimal.ONE;
			for (var taxa : diasCdi.values()) {
				fator = fator.multiply(BigDecimal.ONE.add(taxa.divide(CEM, MathContext.DECIMAL128)),
						MathContext.DECIMAL128);
			}
			cdiPercentual = fator.subtract(BigDecimal.ONE).multiply(CEM).setScale(2, RoundingMode.HALF_UP);
			cdiAte = diasCdi.lastKey();
		}
		return new Resultado(de, hoje, desdeCadastro, List.copyOf(pontos), valorInicial, valorFinal, aportes, resgates,
				ganho, ganhoPercentual, cdiPercentual, cdiAte);
	}

	/**
	 * Fica fora de tudo o que está fora do patrimônio (HU-31): ação, cripto e Tesouro que nunca tiveram cotação, e a
	 * renda fixa ainda sem CDI depois da aplicação.
	 */
	private static boolean temValor(Ativo a, NavigableMap<LocalDate, BigDecimal> cdi) {
		if (a.investimento().getTipo() == Investimento.Tipo.RENDA_FIXA) {
			return ValorAtual.rendaFixa(a.investimento(), a.movimentacoes(), cdi).valorAtual() != null;
		}
		return !a.precos().isEmpty() || a.precoHoje() != null;
	}

	private static BigDecimal total(List<Ativo> ativos, LocalDate dia, LocalDate hoje,
			Map<Ativo, NavigableMap<LocalDate, BigDecimal>> saldos) {
		var soma = BigDecimal.ZERO;
		for (var a : ativos) {
			soma = soma.add(valorEm(a, dia, hoje, saldos.get(a)));
		}
		return soma.setScale(2, RoundingMode.HALF_UP);
	}

	/** @param saldos só na renda fixa: o saldo de cada dia ({@link ValorAtual#saldosPorDia}) */
	static BigDecimal valorEm(Ativo a, LocalDate dia, LocalDate hoje, NavigableMap<LocalDate, BigDecimal> saldos) {
		var ate = a.movimentacoes().stream().filter(m -> !m.getData().isAfter(dia)).toList();
		if (ate.isEmpty()) {
			return BigDecimal.ZERO;
		}
		var i = a.investimento();
		var posicao = Posicao.de(i.getTipo(), ate);
		if (posicao.encerrada()) {
			return BigDecimal.ZERO;
		}
		if (i.getTipo() == Investimento.Tipo.RENDA_FIXA) {
			var saldo = saldos.floorEntry(dia);
			return saldo != null ? saldo.getValue() : posicao.aplicado();
		}
		return posicao.quantidade().multiply(preco(a, dia, hoje));
	}

	/** A cotação de hoje; senão o último preço do dia guardado; senão o preço médio da posição inicial. */
	private static BigDecimal preco(Ativo a, LocalDate dia, LocalDate hoje) {
		if (dia.equals(hoje) && a.precoHoje() != null) {
			return a.precoHoje();
		}
		var guardado = a.precos().floorEntry(dia);
		if (guardado != null) {
			return guardado.getValue();
		}
		var inicial = a.movimentacoes()
			.stream()
			.filter(m -> m.getTipo() == Movimentacao.Tipo.INICIAL)
			.findFirst()
			.orElseThrow();
		return inicial.getPreco() != null ? inicial.getPreco()
				: inicial.getValor().divide(inicial.getQuantidade(), MathContext.DECIMAL128);
	}

	/** O valor em reais: o informado; senão quantidade × preço; no resgate por quantidade, pelo preço daquele dia. */
	private static BigDecimal emReais(Ativo a, Movimentacao m, LocalDate hoje) {
		if (m.getValor() != null) {
			return m.getValor();
		}
		var preco = m.getPreco() != null ? m.getPreco() : preco(a, m.getData(), hoje);
		return m.getQuantidade().multiply(preco);
	}

}
