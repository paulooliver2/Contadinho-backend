package br.com.contadinho.investimentos.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Escolhe a fonte de ativos: a falsa (contadinho.ativos.fonte=falsa) ou brapi + CoinGecko, com as chaves só nas
 * variáveis de ambiente do servidor (RNF-03).
 */
@Configuration
class FontesConfig {

	private static final Duration ESPERA = Duration.ofSeconds(3);

	/** A planilha do Tesouro tem o histórico inteiro e passa de 10 MB. */
	private static final Duration ESPERA_PLANILHA = Duration.ofSeconds(30);

	@Bean
	FonteAtivos fonteAtivos(@Value("${contadinho.ativos.fonte:real}") String fonte,
			@Value("${contadinho.ativos.brapi.token:}") String tokenBrapi,
			@Value("${contadinho.ativos.coingecko.chave:}") String chaveCoinGecko) {
		if (fonte.equals("falsa")) {
			return new FonteFalsa();
		}
		var brapi = new FonteBrapi(builder(), tokenBrapi);
		var coinGecko = new FonteCoinGecko(builder(), chaveCoinGecko);
		return new FonteAtivos() {

			@Override
			public Conferencia conferirTicker(String ticker) {
				return brapi.conferirTicker(ticker);
			}

			@Override
			public BuscaCripto buscarCripto(String texto) {
				return coinGecko.buscarCripto(texto);
			}

			@Override
			public Conferencia conferirCripto(String id) {
				return coinGecko.conferirCripto(id);
			}
		};
	}

	/** HU-31: as cotações de hoje, com a mesma chave de escolha da fonte. */
	@Bean
	FonteCotacoes fonteCotacoes(@Value("${contadinho.ativos.fonte:real}") String fonte,
			@Value("${contadinho.ativos.brapi.token:}") String tokenBrapi,
			@Value("${contadinho.ativos.coingecko.chave:}") String chaveCoinGecko, Clock clock) {
		if (fonte.equals("falsa")) {
			return new CotacoesFalsas(clock);
		}
		var brapi = new CotacoesBrapi(builder(), tokenBrapi);
		var coinGecko = new CotacoesCoinGecko(builder(), chaveCoinGecko);
		var tesouro = new CotacoesTesouro(builder(ESPERA_PLANILHA));
		var bancoCentral = new CotacoesBancoCentral(builder());
		return new FonteCotacoes() {

			@Override
			public Map<String, BigDecimal> acoes(Collection<String> tickers) {
				return brapi.acoes(tickers);
			}

			@Override
			public Map<String, BigDecimal> criptos(Collection<String> ids) {
				return coinGecko.criptos(ids);
			}

			@Override
			public Map<String, Preco> tesouro() {
				return tesouro.tesouro();
			}

			@Override
			public NavigableMap<LocalDate, BigDecimal> cdi(LocalDate de, LocalDate ate) {
				return bancoCentral.cdi(de, ate);
			}
		};
	}

	private static RestClient.Builder builder() {
		return builder(ESPERA);
	}

	private static RestClient.Builder builder(Duration leitura) {
		var fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(ESPERA);
		fabrica.setReadTimeout(leitura);
		return RestClient.builder().requestFactory(fabrica);
	}

}
