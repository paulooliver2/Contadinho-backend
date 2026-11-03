package br.com.contadinho.investimentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import br.com.contadinho.investimentos.service.FonteAtivos.BuscaCripto;
import br.com.contadinho.investimentos.service.FonteAtivos.Conferencia;
import br.com.contadinho.investimentos.service.FonteAtivos.Cripto;
import br.com.contadinho.investimentos.service.FonteBrapi;
import br.com.contadinho.investimentos.service.FonteCoinGecko;
import br.com.contadinho.investimentos.service.FonteFalsa;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** HU-30.1: conferir o ticker na brapi e a cripto na CoinGecko, sem internet nos testes. */
class FontesTests {

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();

	@Test
	void brapiAchaOTicker() {
		servidor.expect(requestTo("https://brapi.dev/api/quote/PETR4?token=chave"))
			.andRespond(withSuccess("{\"results\":[{\"symbol\":\"PETR4\",\"shortName\":\"PETROBRAS PN\","
					+ "\"longName\":\"Petróleo Brasileiro S.A. - Petrobras\"}]}", MediaType.APPLICATION_JSON));

		var r = new FonteBrapi(builder, "chave").conferirTicker("PETR4");

		assertThat(r).isEqualTo(new Conferencia.Existe("PETROBRAS PN", "PETR4"));
		servidor.verify();
	}

	@Test
	void brapiNaoAchaOuFalha() {
		servidor.expect(requestTo("https://brapi.dev/api/quote/PETR9?token=chave")).andRespond(withResourceNotFound());
		servidor.expect(requestTo("https://brapi.dev/api/quote/VALE3?token=chave")).andRespond(withServerError());
		var brapi = new FonteBrapi(builder, "chave");

		assertThat(brapi.conferirTicker("PETR9")).isEqualTo(new Conferencia.NaoExiste());
		assertThat(brapi.conferirTicker("VALE3")).isEqualTo(new Conferencia.Indisponivel());
	}

	@Test
	void brapiSemChaveNemChama() {
		assertThat(new FonteBrapi(builder, "").conferirTicker("PETR4")).isEqualTo(new Conferencia.Indisponivel());
		servidor.verify();
	}

	@Test
	void coinGeckoBuscaComAChave() {
		servidor.expect(requestTo("https://api.coingecko.com/api/v3/search?query=bit"))
			.andExpect(header("x-cg-demo-api-key", "chave"))
			.andRespond(withSuccess("{\"coins\":[{\"id\":\"bitcoin\",\"symbol\":\"BTC\",\"name\":\"Bitcoin\"},"
					+ "{\"id\":\"bitcoin-cash\",\"symbol\":\"BCH\",\"name\":\"Bitcoin Cash\"}]}",
					MediaType.APPLICATION_JSON));

		var r = new FonteCoinGecko(builder, "chave").buscarCripto("bit");

		assertThat(r).isEqualTo(new BuscaCripto.Encontradas(List.of(new Cripto("bitcoin", "BTC", "Bitcoin"),
				new Cripto("bitcoin-cash", "BCH", "Bitcoin Cash"))));
	}

	@Test
	void coinGeckoFalhaOuNaoAcha() {
		servidor.expect(requestTo("https://api.coingecko.com/api/v3/search?query=eth")).andRespond(withServerError());
		servidor.expect(requestTo(
				"https://api.coingecko.com/api/v3/coins/nao-existe?localization=false&tickers=false&market_data=false"
						+ "&community_data=false&developer_data=false"))
			.andRespond(withResourceNotFound());
		servidor.expect(requestTo(
				"https://api.coingecko.com/api/v3/coins/bitcoin?localization=false&tickers=false&market_data=false"
						+ "&community_data=false&developer_data=false"))
			.andRespond(withSuccess("{\"id\":\"bitcoin\",\"symbol\":\"btc\",\"name\":\"Bitcoin\"}",
					MediaType.APPLICATION_JSON));
		var coinGecko = new FonteCoinGecko(builder, "");

		assertThat(coinGecko.buscarCripto("eth")).isEqualTo(new BuscaCripto.Indisponivel());
		assertThat(coinGecko.conferirCripto("nao-existe")).isEqualTo(new Conferencia.NaoExiste());
		assertThat(coinGecko.conferirCripto("bitcoin")).isEqualTo(new Conferencia.Existe("Bitcoin", "BTC"));
	}

	@Test
	void falsaConheceAlgunsAtivos() {
		var falsa = new FonteFalsa();

		assertThat(falsa.conferirTicker("PETR4")).isEqualTo(new Conferencia.Existe("Petrobras PN", "PETR4"));
		assertThat(falsa.conferirTicker("PETR9")).isEqualTo(new Conferencia.NaoExiste());
		assertThat(falsa.conferirTicker("FORA3")).isEqualTo(new Conferencia.Indisponivel());
		assertThat(falsa.buscarCripto("bit")).isEqualTo(
				new BuscaCripto.Encontradas(List.of(new Cripto("bitcoin", "BTC", "Bitcoin"))));
		assertThat(falsa.buscarCripto("fora")).isEqualTo(new BuscaCripto.Indisponivel());
		assertThat(falsa.conferirCripto("pepe")).isEqualTo(new Conferencia.Existe("Pepe", "PEPE"));
	}

}
