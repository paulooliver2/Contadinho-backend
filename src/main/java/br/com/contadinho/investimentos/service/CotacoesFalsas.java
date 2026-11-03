package br.com.contadinho.investimentos.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Preços fixos para o docker-local, o E2E e os testes (contadinho.ativos.fonte=falsa). FORA3 e qualquer título
 * fora da lista ficam sem preço.
 */
public class CotacoesFalsas implements FonteCotacoes {

	private static final Map<String, BigDecimal> ACOES = Map.of("PETR4", new BigDecimal("38.00"), "VALE3",
			new BigDecimal("60.00"), "ITUB4", new BigDecimal("35.00"), "BOVA11", new BigDecimal("130.00"));

	private static final Map<String, BigDecimal> CRIPTOS = Map.of("bitcoin", new BigDecimal("400000.00"), "ethereum",
			new BigDecimal("20000.00"), "pepe", new BigDecimal("0.00002"));

	private static final BigDecimal CDI = new BigDecimal("0.04");

	private final Clock clock;

	public CotacoesFalsas(Clock clock) {
		this.clock = clock;
	}

	@Override
	public Map<String, BigDecimal> acoes(Collection<String> tickers) {
		var precos = new LinkedHashMap<String, BigDecimal>();
		tickers.stream().filter(ACOES::containsKey).forEach(t -> precos.put(t, ACOES.get(t)));
		return precos;
	}

	@Override
	public Map<String, BigDecimal> criptos(Collection<String> ids) {
		var precos = new LinkedHashMap<String, BigDecimal>();
		ids.stream().filter(CRIPTOS::containsKey).forEach(id -> precos.put(id, CRIPTOS.get(id)));
		return precos;
	}

	@Override
	public Map<String, Preco> tesouro() {
		var dia = LocalDate.now(clock);
		while (!diaDeSemana(dia)) {
			dia = dia.minusDays(1);
		}
		return Map.of("Tesouro IPCA+ 2035", new Preco(new BigDecimal("3400.00"), dia));
	}

	@Override
	public NavigableMap<LocalDate, BigDecimal> cdi(LocalDate de, LocalDate ate) {
		var cdi = new TreeMap<LocalDate, BigDecimal>();
		for (var dia = de; !dia.isAfter(ate); dia = dia.plusDays(1)) {
			if (diaDeSemana(dia)) {
				cdi.put(dia, CDI);
			}
		}
		return cdi;
	}

	private static boolean diaDeSemana(LocalDate dia) {
		return dia.getDayOfWeek() != DayOfWeek.SATURDAY && dia.getDayOfWeek() != DayOfWeek.SUNDAY;
	}

}
