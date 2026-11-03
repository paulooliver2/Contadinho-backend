package br.com.contadinho.investimentos.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import br.com.contadinho.investimentos.domain.DiasUteis;

/**
 * Preços fixos para o docker-local, o E2E e os testes (contadinho.ativos.fonte=falsa). FORA3 e qualquer título
 * fora da lista ficam sem preço.
 */
public class CotacoesFalsas implements FonteCotacoes {

	private static final Map<String, BigDecimal> ACOES = Map.of("PETR4", new BigDecimal("38.00"), "VALE3",
			new BigDecimal("60.00"), "ITUB4", new BigDecimal("35.00"), "BOVA11", new BigDecimal("130.00"),
			"HGLG11", new BigDecimal("160.00"));

	private static final Map<String, BigDecimal> CRIPTOS = Map.of("bitcoin", new BigDecimal("400000.00"), "ethereum",
			new BigDecimal("20000.00"), "pepe", new BigDecimal("0.00002"));

	private static final Map<String, BigDecimal> TESOURO = Map.of("Tesouro IPCA+ 2035", new BigDecimal("3400.00"),
			"Tesouro Selic 2029", new BigDecimal("17000.00"));

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
	public Map<String, List<Preco>> tesouro(Map<String, LocalDate> desde) {
		var precos = new java.util.HashMap<String, List<Preco>>();
		TESOURO.forEach((codigo, hoje) -> {
			var inicio = desde.get(codigo);
			if (inicio != null) {
				precos.put(codigo, serie(hoje, inicio));
			}
		});
		return precos;
	}

	/** O preço no último dia de semana e R$ 2,00 a menos a cada dia de semana para trás, até {@code inicio}. */
	private List<Preco> serie(BigDecimal ultimo, LocalDate inicio) {
		var dias = new ArrayList<Preco>();
		var dia = DiasUteis.ultimo(LocalDate.now(clock));
		var preco = ultimo;
		do {
			dias.addFirst(new Preco(preco, dia));
			dia = DiasUteis.anterior(dia);
			preco = preco.subtract(new BigDecimal("2.00"));
		}
		while (!dia.isBefore(inicio));
		return List.copyOf(dias);
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
