package br.com.contadinho.investimentos.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fonte sem internet, para o docker-local, o E2E e os testes (contadinho.ativos.fonte=falsa). O ticker FORA3 e a
 * busca "fora" simulam a fonte fora do ar.
 */
public class FonteFalsa implements FonteAtivos {

	private static final Map<String, String> TICKERS = Map.of("PETR4", "Petrobras PN", "VALE3", "Vale ON", "ITUB4",
			"Itaú Unibanco PN", "BOVA11", "iShares Ibovespa");

	private static final List<Cripto> CRIPTOS = List.of(new Cripto("bitcoin", "BTC", "Bitcoin"),
			new Cripto("ethereum", "ETH", "Ethereum"), new Cripto("pepe", "PEPE", "Pepe"));

	@Override
	public Conferencia conferirTicker(String ticker) {
		if (ticker.equals("FORA3")) {
			return new Conferencia.Indisponivel();
		}
		var nome = TICKERS.get(ticker);
		return nome == null ? new Conferencia.NaoExiste() : new Conferencia.Existe(nome, ticker);
	}

	@Override
	public BuscaCripto buscarCripto(String texto) {
		var busca = texto.toLowerCase(Locale.ROOT);
		if (busca.equals("fora")) {
			return new BuscaCripto.Indisponivel();
		}
		return new BuscaCripto.Encontradas(CRIPTOS.stream()
			.filter(c -> c.id().contains(busca) || c.simbolo().toLowerCase(Locale.ROOT).contains(busca)
					|| c.nome().toLowerCase(Locale.ROOT).contains(busca))
			.toList());
	}

	@Override
	public Conferencia conferirCripto(String id) {
		return CRIPTOS.stream()
			.filter(c -> c.id().equals(id))
			.findFirst()
			.<Conferencia>map(c -> new Conferencia.Existe(c.nome(), c.simbolo()))
			.orElse(new Conferencia.NaoExiste());
	}

}
