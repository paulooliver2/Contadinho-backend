package br.com.contadinho.investimentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import br.com.contadinho.investimentos.service.CotacoesBancoCentral;
import br.com.contadinho.investimentos.service.CotacoesBrapi;
import br.com.contadinho.investimentos.service.CotacoesCoinGecko;
import br.com.contadinho.investimentos.service.CotacoesTesouro;
import br.com.contadinho.investimentos.service.FonteCotacoes;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** HU-31: as cotações de cada fonte, sem internet nos testes. */
class CotacoesTests {

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();

	@Test
	void brapiLePreco() {
		servidor.expect(requestTo("https://brapi.dev/api/quote/PETR4?token=chave"))
			.andRespond(withSuccess("{\"results\":[{\"symbol\":\"PETR4\",\"regularMarketPrice\":38.5}]}",
					MediaType.APPLICATION_JSON));
		servidor.expect(requestTo("https://brapi.dev/api/quote/PETR9?token=chave")).andRespond(withResourceNotFound());

		var precos = new CotacoesBrapi(builder, "chave").acoes(List.of("PETR4", "PETR9"));

		assertThat(precos).containsOnlyKeys("PETR4");
		assertThat(precos.get("PETR4")).isEqualByComparingTo("38.5");
		servidor.verify();
	}

	@Test
	void brapiForaDoAr() {
		servidor.expect(requestTo("https://brapi.dev/api/quote/VALE3?token=chave")).andRespond(withServerError());

		assertThatThrownBy(() -> new CotacoesBrapi(builder, "chave").acoes(List.of("VALE3")))
			.isInstanceOf(FonteCotacoes.Indisponivel.class);
		assertThatThrownBy(() -> new CotacoesBrapi(RestClient.builder(), "").acoes(List.of("VALE3")))
			.isInstanceOf(FonteCotacoes.Indisponivel.class);
	}

	@Test
	void coinGeckoVariosNumPedido() {
		servidor.expect(requestTo("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,pepe&vs_currencies=brl"))
			.andExpect(header("x-cg-demo-api-key", "chave"))
			.andRespond(withSuccess("{\"bitcoin\":{\"brl\":400000},\"pepe\":{\"brl\":0.00002}}",
					MediaType.APPLICATION_JSON));

		var precos = new CotacoesCoinGecko(builder, "chave").criptos(List.of("bitcoin", "pepe"));

		assertThat(precos.get("bitcoin")).isEqualByComparingTo("400000");
		assertThat(precos.get("pepe")).isEqualByComparingTo("0.00002");
	}

	@Test
	void coinGeckoForaDoAr() {
		servidor.expect(requestTo("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=brl"))
			.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> new CotacoesCoinGecko(builder, "").criptos(List.of("bitcoin")))
			.isInstanceOf(FonteCotacoes.Indisponivel.class);
	}

	@Test
	void tesouroFicaComADataMaisRecente() {
		var csv = String.join("\n", "﻿Tipo Titulo;Data Vencimento;Data Base;Taxa Compra Manha;Taxa Venda Manha;"
				+ "PU Compra Manha;PU Venda Manha;PU Base Manha",
				"Tesouro IPCA+;15/05/2035;30/10/2026;7,10;7,22;3390,10;3380,55;3380,00",
				"Tesouro IPCA+;15/05/2035;31/10/2026;7,08;7,20;3401,00;3400,25;3399,00",
				"linha quebrada;sem;campos",
				"Tesouro Selic;01/03/2029;31/10/2026;0,05;0,06;17000,00;16950,12;16950,00",
				"Tesouro IPCA+;15/05/2035;29/10/2026;7,15;7,25;3370,00;3365,00;3365,00");
		servidor.expect(requestTo(CotacoesTesouro.ENDERECO)).andRespond(withSuccess(csv, MediaType.TEXT_PLAIN));

		var precos = new CotacoesTesouro(builder).tesouro();

		assertThat(precos).containsOnlyKeys("Tesouro IPCA+ 2035", "Tesouro Selic 2029");
		assertThat(precos.get("Tesouro IPCA+ 2035").valor()).isEqualByComparingTo("3400.25");
		assertThat(precos.get("Tesouro IPCA+ 2035").referencia()).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(precos.get("Tesouro Selic 2029").valor()).isEqualByComparingTo("16950.12");
	}

	@Test
	void educaERendaPeloAnoDoNome() {
		// A planilha traz o último pagamento: Educa+ 2030 paga até 2034, Renda+ 2030 até 2049.
		var csv = String.join("\n", "Tipo Titulo;Data Vencimento;Data Base;PU Venda Manha",
				"Tesouro Educa+;15/12/2034;31/10/2026;3100,00", "Tesouro Educa+;15/12/2030;31/10/2026;3900,00",
				"Tesouro Renda+ Aposentadoria Extra;15/12/2049;31/10/2026;1500,00");
		servidor.expect(requestTo(CotacoesTesouro.ENDERECO)).andRespond(withSuccess(csv, MediaType.TEXT_PLAIN));

		var precos = new CotacoesTesouro(builder).tesouro();

		assertThat(precos.get("Tesouro Educa+ 2030").valor()).isEqualByComparingTo("3100.00");
		assertThat(precos.get("Tesouro Educa+ 2026").valor()).isEqualByComparingTo("3900.00");
		assertThat(precos.get("Tesouro Renda+ Aposentadoria Extra 2030").valor()).isEqualByComparingTo("1500.00");
	}

	@Test
	void bancoCentralCdi() {
		servidor.expect(requestTo("https://api.bcb.gov.br/dados/serie/bcdata.sgs.12/dados?formato=json"
				+ "&dataInicial=29/10/2026&dataFinal=31/10/2026"))
			.andRespond(withSuccess("[{\"data\":\"29/10/2026\",\"valor\":\"0.040168\"},"
					+ "{\"data\":\"30/10/2026\",\"valor\":\"0.040168\"}]", MediaType.APPLICATION_JSON));

		var cdi = new CotacoesBancoCentral(builder).cdi(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 31));

		assertThat(cdi).containsOnlyKeys(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30));
		assertThat(cdi.get(LocalDate.of(2026, 10, 30))).isEqualByComparingTo(new BigDecimal("0.040168"));
	}

	@Test
	void bancoCentralForaDoAr() {
		servidor.expect(requestTo("https://api.bcb.gov.br/dados/serie/bcdata.sgs.12/dados?formato=json"
				+ "&dataInicial=29/10/2026&dataFinal=31/10/2026"))
			.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(
				() -> new CotacoesBancoCentral(builder).cdi(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 31)))
			.isInstanceOf(FonteCotacoes.Indisponivel.class);
	}

}
