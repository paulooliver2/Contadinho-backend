package br.com.contadinho.investimentos.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** A última cotação na brapi, um ticker por pedido (plano grátis). Sem chave, fica indisponível. */
public class CotacoesBrapi {

	private final RestClient http;

	private final String token;

	public CotacoesBrapi(RestClient.Builder builder, String token) {
		this.http = builder.baseUrl("https://brapi.dev").build();
		this.token = token == null ? "" : token.trim();
	}

	record Resposta(List<Resultado> results) {
	}

	record Resultado(String symbol, BigDecimal regularMarketPrice) {
	}

	public Map<String, BigDecimal> acoes(Collection<String> tickers) {
		if (token.isEmpty()) {
			throw new FonteCotacoes.Indisponivel("brapi", null);
		}
		var precos = new LinkedHashMap<String, BigDecimal>();
		for (var ticker : tickers) {
			try {
				var r = http.get().uri("/api/quote/{ticker}?token={token}", ticker, token).retrieve().body(Resposta.class);
				if (r != null && r.results() != null && !r.results().isEmpty()
						&& r.results().getFirst().regularMarketPrice() != null) {
					precos.put(ticker, r.results().getFirst().regularMarketPrice());
				}
			}
			catch (RestClientResponseException e) {
				if (!e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
					throw new FonteCotacoes.Indisponivel("brapi", e);
				}
			}
			catch (RestClientException e) {
				throw new FonteCotacoes.Indisponivel("brapi", e);
			}
		}
		return precos;
	}

}
