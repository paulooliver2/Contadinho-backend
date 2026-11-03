package br.com.contadinho.investimentos.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Busca e confere cripto na CoinGecko. Com a chave do plano Demo (COINGECKO_CHAVE) ela vai no cabeçalho; sem ela,
 * usa a API pública, com limite menor.
 */
public class FonteCoinGecko {

	private static final int MAXIMO = 10;

	private final RestClient http;

	public FonteCoinGecko(RestClient.Builder builder, String chave) {
		var b = builder.baseUrl("https://api.coingecko.com");
		if (chave != null && !chave.isBlank()) {
			b = b.defaultHeader("x-cg-demo-api-key", chave.trim());
		}
		this.http = b.build();
	}

	record Busca(List<Moeda> coins) {
	}

	record Moeda(String id, String symbol, String name) {
	}

	public FonteAtivos.BuscaCripto buscarCripto(String texto) {
		try {
			var r = http.get().uri("/api/v3/search?query={texto}", texto).retrieve().body(Busca.class);
			var moedas = r == null || r.coins() == null ? List.<Moeda>of() : r.coins();
			return new FonteAtivos.BuscaCripto.Encontradas(moedas.stream()
				.limit(MAXIMO)
				.map(m -> new FonteAtivos.Cripto(m.id(), m.symbol().toUpperCase(Locale.ROOT), m.name()))
				.toList());
		}
		catch (RestClientException e) {
			return new FonteAtivos.BuscaCripto.Indisponivel();
		}
	}

	public FonteAtivos.Conferencia conferirCripto(String id) {
		try {
			var m = http.get()
				.uri("/api/v3/coins/{id}?localization=false&tickers=false&market_data=false&community_data=false"
						+ "&developer_data=false", id)
				.retrieve()
				.body(Moeda.class);
			if (m == null || m.id() == null) {
				return new FonteAtivos.Conferencia.NaoExiste();
			}
			return new FonteAtivos.Conferencia.Existe(m.name(), m.symbol().toUpperCase(Locale.ROOT));
		}
		catch (RestClientResponseException e) {
			return e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND) ? new FonteAtivos.Conferencia.NaoExiste()
					: new FonteAtivos.Conferencia.Indisponivel();
		}
		catch (RestClientException e) {
			return new FonteAtivos.Conferencia.Indisponivel();
		}
	}

}
