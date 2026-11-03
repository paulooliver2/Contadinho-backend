package br.com.contadinho.investimentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import br.com.contadinho.ClienteTeste;
import br.com.contadinho.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** HU-32: a evolução por período, a distribuição por classe e a troca entre Ação/ETF e FII. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EvolucaoApiTests {

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		jdbc.update("delete from cotacao");
		jdbc.update("delete from cotacao_diaria");
		jdbc.update("delete from cdi_diario");
		jdbc.update("delete from atualizacao_fonte");
	}

	private static BigDecimal n(Object valor) {
		return new BigDecimal(String.valueOf(valor));
	}

	private String criar(Map<String, Object> corpo) {
		var r = api.post("/api/investimentos", corpo, dono);
		assertThat(r.status()).isEqualTo(201);
		return r.texto("id");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> campos(ClienteTeste.Resposta r) {
		assertThat(r.status()).isEqualTo(400);
		return (Map<String, Object>) r.objeto().get("campos");
	}

	@Test
	@SuppressWarnings("unchecked")
	void valorFinalIgualAoPatrimonio() {
		criar(Map.of("tipo", "ACAO", "codigo", "PETR4", "quantidade", 100, "preco", 30));
		criar(Map.of("tipo", "ACAO", "codigo", "HGLG11", "quantidade", 10, "preco", 150, "fii", true));
		// Aplicado hoje: ainda sem CDI, fica fora do patrimônio e da evolução.
		criar(Map.of("tipo", "RENDA_FIXA", "produto", "CDB", "emissor", "Banco X", "valor", 10000, "dataAplicacao",
				java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString(), "indexador", "CDI",
				"taxa", 110, "vencimento", java.time.LocalDate.now().plusYears(2).toString()));
		var carteira = api.post("/api/investimentos/atualizar", Map.of(), dono);

		var r = api.get("/api/investimentos/evolucao?periodo=MES", dono);

		assertThat(r.status()).isEqualTo(200);
		var e = r.objeto();
		assertThat(e).containsEntry("desdeCadastro", true);
		assertThat(n(e.get("valorFinal"))).isEqualByComparingTo(n(carteira.objeto().get("patrimonio")))
			.isEqualByComparingTo("5400.00");
		assertThat(n(e.get("aportes"))).isEqualByComparingTo("4500.00");
		assertThat(n(e.get("ganho"))).isEqualByComparingTo("900.00");
		var fatias = (List<Map<String, Object>>) carteira.objeto().get("distribuicao");
		assertThat(fatias).extracting(f -> f.get("classe")).containsExactly("ACOES", "FIIS");
		assertThat(fatias).extracting(f -> n(f.get("percentual")).toPlainString()).containsExactly("70.37", "29.63");
		assertThat(n(fatias.get(1).get("valor"))).isEqualByComparingTo("1600");
	}

	@Test
	@SuppressWarnings("unchecked")
	void semInvestimento() {
		var r = api.get("/api/investimentos/evolucao?periodo=DIA", dono);

		assertThat(r.status()).isEqualTo(200);
		assertThat((List<Object>) r.objeto().get("pontos")).isEmpty();
		assertThat(r.objeto().get("ganhoPercentual")).isNull();
		assertThat((List<Object>) api.get("/api/investimentos", dono).objeto().get("distribuicao")).isEmpty();
	}

	@Test
	void periodoInvalido() {
		assertThat(api.get("/api/investimentos/evolucao?periodo=SEMANA", dono).status()).isEqualTo(400);
	}

	@Test
	@SuppressWarnings("unchecked")
	void trocarParaFii() {
		var petr = criar(Map.of("tipo", "ACAO", "codigo", "PETR4", "quantidade", 100, "preco", 30));
		var cdb = criar(Map.of("tipo", "RENDA_FIXA", "produto", "CDB", "emissor", "Banco X", "valor", 10000,
				"dataAplicacao", java.time.LocalDate.now().minusDays(5).toString(), "indexador", "CDI", "taxa", 110,
				"vencimento", java.time.LocalDate.now().plusYears(2).toString()));
		api.post("/api/investimentos/atualizar", Map.of(), dono);

		var r = api.patch("/api/investimentos/" + petr, Map.of("fii", true), dono);

		assertThat(r.status()).isEqualTo(200);
		assertThat(r.objeto()).containsEntry("fii", true);
		var fatias = (List<Map<String, Object>>) api.get("/api/investimentos", dono).objeto().get("distribuicao");
		assertThat(fatias).extracting(f -> f.get("classe")).contains("FIIS").doesNotContain("ACOES");
		assertThat(campos(api.patch("/api/investimentos/" + cdb, Map.of("fii", true), dono))).containsKey("fii");
		assertThat(api.patch("/api/investimentos/" + petr, Map.of("fii", false), api.novoDono()).status())
			.isEqualTo(404);
	}

	@Test
	void cadastroComFii() {
		var r = api.post("/api/investimentos",
				Map.of("tipo", "ACAO", "codigo", "HGLG11", "quantidade", 10, "preco", 150, "fii", true), dono);

		assertThat(r.status()).isEqualTo(201);
		assertThat(r.objeto()).containsEntry("fii", true);
		assertThat(campos(api.post("/api/investimentos",
				Map.of("tipo", "CRIPTO", "codigo", "bitcoin", "quantidade", 1, "preco", 10, "fii", true), dono)))
			.containsKey("fii");
	}

}
