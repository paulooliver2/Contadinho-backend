package br.com.contadinho.investimentos.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** O preço em reais de todas as criptos num pedido só (simple/price). A chave do plano Demo vai no cabeçalho. */
public class CotacoesCoinGecko {

	/** Os ids vêm da busca da CoinGecko (HU-30); qualquer outra coisa não entra na URL. */
	private static final Pattern ID = Pattern.compile("[a-z0-9-]+");

	private final RestClient http;

	public CotacoesCoinGecko(RestClient.Builder builder, String chave) {
		var b = builder.baseUrl("https://api.coingecko.com");
		if (chave != null && !chave.isBlank()) {
			b = b.defaultHeader("x-cg-demo-api-key", chave.trim());
		}
		this.http = b.build();
	}

	public Map<String, BigDecimal> criptos(Collection<String> ids) {
		var validos = ids.stream().filter(id -> ID.matcher(id).matches()).collect(Collectors.joining(","));
		if (validos.isEmpty()) {
			return Map.of();
		}
		try {
			Map<String, Map<String, BigDecimal>> r = http.get()
				.uri("/api/v3/simple/price?ids=" + validos + "&vs_currencies=brl")
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
			var precos = new LinkedHashMap<String, BigDecimal>();
			if (r != null) {
				r.forEach((id, moedas) -> {
					if (moedas != null && moedas.get("brl") != null) {
						precos.put(id, moedas.get("brl"));
					}
				});
			}
			return precos;
		}
		catch (RestClientException e) {
			throw new FonteCotacoes.Indisponivel("coingecko", e);
		}
	}

}
