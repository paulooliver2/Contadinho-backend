package br.com.contadinho.investimentos.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Confere o ticker na brapi (plano grátis, com a chave em BRAPI_TOKEN). Sem chave, fica indisponível. */
public class FonteBrapi {

	private final RestClient http;

	private final String token;

	public FonteBrapi(RestClient.Builder builder, String token) {
		this.http = builder.baseUrl("https://brapi.dev").build();
		this.token = token == null ? "" : token.trim();
	}

	record Resposta(List<Resultado> results) {
	}

	record Resultado(String symbol, String shortName, String longName) {
	}

	public FonteAtivos.Conferencia conferirTicker(String ticker) {
		if (token.isEmpty()) {
			return new FonteAtivos.Conferencia.Indisponivel();
		}
		try {
			var r = http.get().uri("/api/quote/{ticker}?token={token}", ticker, token).retrieve().body(Resposta.class);
			if (r == null || r.results() == null || r.results().isEmpty()) {
				return new FonteAtivos.Conferencia.NaoExiste();
			}
			var achado = r.results().getFirst();
			var nome = achado.shortName() != null ? achado.shortName() : achado.longName();
			return new FonteAtivos.Conferencia.Existe(nome, achado.symbol() != null ? achado.symbol() : ticker);
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
