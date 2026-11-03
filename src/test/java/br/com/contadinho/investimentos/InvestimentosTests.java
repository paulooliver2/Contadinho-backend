package br.com.contadinho.investimentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
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

/** HU-30: cadastrar posições, aportar e resgatar, com a fonte falsa (PETR4, FORA3, bitcoin, pepe…). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InvestimentosTests {

	private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

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
	}

	private static BigDecimal n(Object valor) {
		return new BigDecimal(String.valueOf(valor));
	}

	private ClienteTeste.Resposta criar(Map<String, Object> corpo) {
		return api.post("/api/investimentos", corpo, dono);
	}

	private Map<String, Object> acao(String ticker, Object quantidade, Object preco) {
		return Map.of("tipo", "ACAO", "codigo", ticker, "quantidade", quantidade, "preco", preco);
	}

	private Map<String, Object> cdb() {
		return Map.of("tipo", "RENDA_FIXA", "produto", "CDB", "emissor", "Banco X", "valor", 10000, "dataAplicacao",
				HOJE.minusMonths(2).toString(), "indexador", "CDI", "taxa", 110, "vencimento",
				HOJE.plusYears(2).toString());
	}

	private ClienteTeste.Resposta movimentar(String id, Map<String, Object> corpo) {
		return api.post("/api/investimentos/" + id + "/movimentacoes", corpo, dono);
	}

	@SuppressWarnings("unchecked")
	private static String erro(ClienteTeste.Resposta r, String campo) {
		assertThat(r.status()).isEqualTo(400);
		return (String) ((Map<String, Object>) r.objeto().get("campos")).get(campo);
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> movimentacoes(ClienteTeste.Resposta r) {
		return (List<Map<String, Object>>) r.objeto().get("movimentacoes");
	}

	@Test
	void cadastraAcaoNormalizandoOTicker() {
		var r = criar(acao(" petr4f ", 100, 30));

		assertThat(r.status()).isEqualTo(201);
		assertThat(r.objeto()).containsEntry("codigo", "PETR4").containsEntry("nome", "Petrobras PN")
			.containsEntry("conferido", true).containsEntry("encerrada", false);
		assertThat(n(r.objeto().get("quantidade"))).isEqualByComparingTo("100");
		assertThat(n(r.objeto().get("precoMedio"))).isEqualByComparingTo("30");
		assertThat(n(r.objeto().get("aplicado"))).isEqualByComparingTo("3000");
		assertThat(movimentacoes(r)).singleElement().satisfies(m -> {
			assertThat(m).containsEntry("tipo", "INICIAL").containsEntry("data", HOJE.toString());
		});
	}

	@Test
	void tickerInexistenteEhErroEForaDoArPassaSemConferir() {
		assertThat(erro(criar(acao("PETR9", 1, 10)), "codigo")).isEqualTo("Não achei PETR9 na B3.");
		assertThat(erro(criar(acao("PE-TR", 1, 10)), "codigo")).isNotBlank();

		var fora = criar(acao("FORA3", 1, 10));
		assertThat(fora.status()).isEqualTo(201);
		assertThat(fora.objeto()).containsEntry("conferido", false).containsEntry("codigo", "FORA3");
	}

	@Test
	void camposObrigatoriosECasas() {
		assertThat(erro(criar(Map.of("tipo", "ACAO", "codigo", "PETR4", "preco", 10)), "quantidade")).isNotBlank();
		assertThat(erro(criar(acao("PETR4", 1.5, 10)), "quantidade")).isNotBlank();
		assertThat(erro(criar(acao("PETR4", 1, 0)), "preco")).isNotBlank();
		assertThat(erro(criar(Map.of("tipo", "TESOURO", "tesouroTipo", "IPCA", "tesouroAno", HOJE.getYear() + 9,
				"quantidade", 1.375, "valor", 100)), "quantidade")).isNotBlank();
		assertThat(erro(criar(Map.of("tipo", "TESOURO", "tesouroTipo", "IPCA", "tesouroAno", HOJE.getYear() - 1,
				"quantidade", 1, "valor", 100)), "tesouroAno")).isNotBlank();
		var semTaxa = new HashMap<>(cdb());
		semTaxa.remove("taxa");
		assertThat(erro(criar(semTaxa), "taxa")).isNotBlank();
		var venceAntes = new HashMap<>(cdb());
		venceAntes.put("vencimento", HOJE.minusMonths(3).toString());
		assertThat(erro(criar(venceAntes), "vencimento")).isNotBlank();
		var futura = new HashMap<>(cdb());
		futura.put("dataAplicacao", HOJE.plusDays(1).toString());
		assertThat(erro(criar(futura), "dataAplicacao")).isNotBlank();
	}

	@Test
	void tesouroComONomeOficial() {
		var ano = HOJE.getYear() + 9;
		var r = criar(Map.of("tipo", "TESOURO", "tesouroTipo", "IPCA_JUROS", "tesouroAno", ano, "quantidade", 1.37,
				"valor", 4500));

		assertThat(r.status()).isEqualTo(201);
		assertThat(r.objeto()).containsEntry("codigo", "Tesouro IPCA+ com Juros Semestrais " + ano);
		assertThat(n(r.objeto().get("aplicado"))).isEqualByComparingTo("4500");
	}

	@Test
	void repetirAcaoEhAporteMasCdbPodeRepetir() {
		criar(acao("PETR4", 1, 10));

		assertThat(erro(criar(acao("petr4", 1, 10)), "codigo")).isEqualTo("Você já tem PETR4. Use Aportar.");
		assertThat(criar(cdb()).status()).isEqualTo(201);
		assertThat(criar(cdb()).status()).isEqualTo(201);
	}

	@Test
	void aporteEResgateSeguemARn48() {
		var id = criar(acao("PETR4", 100, 30)).texto("id");

		var aporte = movimentar(id, Map.of("tipo", "APORTE", "quantidade", 100, "preco", 40));
		assertThat(aporte.status()).isEqualTo(201);
		assertThat(n(aporte.objeto().get("precoMedio"))).isEqualByComparingTo("35");

		var resgate = movimentar(id, Map.of("tipo", "RESGATE", "quantidade", 150));
		assertThat(n(resgate.objeto().get("quantidade"))).isEqualByComparingTo("50");
		assertThat(n(resgate.objeto().get("precoMedio"))).isEqualByComparingTo("35");

		assertThat(erro(movimentar(id, Map.of("tipo", "RESGATE", "quantidade", 51)), "quantidade")).isNotBlank();
		assertThat(erro(movimentar(id, Map.of("tipo", "APORTE", "quantidade", 1, "preco", 1, "data",
				HOJE.plusDays(1).toString())), "data")).isNotBlank();
		assertThat(erro(movimentar(id, Map.of("tipo", "APORTE", "quantidade", 1, "preco", 1, "data",
				HOJE.minusDays(1).toString())), "data")).isNotBlank();

		var encerrada = movimentar(id, Map.of("tipo", "RESGATE", "quantidade", 50));
		assertThat(encerrada.objeto()).containsEntry("encerrada", true);
	}

	@Test
	void excluirMovimentacaoNaoDeixaPassoNegativo() {
		var id = criar(acao("PETR4", 100, 30)).texto("id");
		var aporte = movimentar(id, Map.of("tipo", "APORTE", "quantidade", 100, "preco", 40));
		var aporteId = (String) movimentacoes(aporte).stream()
			.filter(m -> m.get("tipo").equals("APORTE"))
			.findFirst()
			.orElseThrow()
			.get("id");
		movimentar(id, Map.of("tipo", "RESGATE", "quantidade", 150));

		var excluir = api.delete("/api/investimentos/" + id + "/movimentacoes/" + aporteId, dono);
		assertThat(excluir.status()).isEqualTo(400);
		assertThat(n(api.get("/api/investimentos/" + id, dono).objeto().get("quantidade"))).isEqualByComparingTo("50");

		var inicialId = (String) movimentacoes(api.get("/api/investimentos/" + id, dono)).stream()
			.filter(m -> m.get("tipo").equals("INICIAL"))
			.findFirst()
			.orElseThrow()
			.get("id");
		assertThat(api.delete("/api/investimentos/" + id + "/movimentacoes/" + inicialId, dono).status())
			.isEqualTo(400);
	}

	@Test
	void rendaFixaSoResgataEEncerraComTotal() {
		var id = criar(cdb()).texto("id");

		assertThat(movimentar(id, Map.of("tipo", "APORTE", "valor", 100)).status()).isEqualTo(400);
		var parcial = movimentar(id, Map.of("tipo", "RESGATE", "valor", 3000));
		assertThat(n(parcial.objeto().get("aplicado"))).isEqualByComparingTo("7000");
		var total = movimentar(id, Map.of("tipo", "RESGATE", "valor", 7500, "total", true));
		assertThat(total.objeto()).containsEntry("encerrada", true);

		assertThat(movimentar(id, Map.of("tipo", "RESGATE", "valor", 1)).status()).isEqualTo(400);
	}

	@Test
	void criptoPelaBusca() {
		var busca = api.get("/api/investimentos/cripto?busca=bit", dono);
		assertThat(busca.status()).isEqualTo(200);
		assertThat(busca.lista()).singleElement().satisfies(c -> assertThat(c).containsEntry("id", "bitcoin")
			.containsEntry("simbolo", "BTC").containsEntry("nome", "Bitcoin"));
		assertThat(api.get("/api/investimentos/cripto?busca=fora", dono).status()).isEqualTo(503);
		assertThat(api.get("/api/investimentos/cripto?busca=b", dono).status()).isEqualTo(400);

		var pepe = criar(Map.of("tipo", "CRIPTO", "codigo", "pepe", "quantidade", 1000000, "preco", 0.00001234));
		assertThat(pepe.status()).isEqualTo(201);
		assertThat(pepe.objeto()).containsEntry("simbolo", "PEPE").containsEntry("nome", "Pepe");
		assertThat(n(pepe.objeto().get("precoMedio"))).isEqualByComparingTo("0.00001234");
		assertThat(n(pepe.objeto().get("aplicado"))).isEqualByComparingTo("12.34");

		assertThat(erro(criar(Map.of("tipo", "CRIPTO", "codigo", "nao-existe", "quantidade", 1, "preco", 1)),
				"codigo")).isNotBlank();
	}

	@Test
	void carteiraSomaOAplicadoEExcluiPosicao() {
		var acao = criar(acao("PETR4", 100, 30)).texto("id");
		criar(cdb());

		var carteira = api.get("/api/investimentos", dono);
		assertThat(n(carteira.objeto().get("totalAplicado"))).isEqualByComparingTo("13000");
		assertThat((List<?>) carteira.objeto().get("posicoes")).hasSize(2);

		assertThat(api.delete("/api/investimentos/" + acao, dono).status()).isEqualTo(204);
		assertThat(n(api.get("/api/investimentos", dono).objeto().get("totalAplicado"))).isEqualByComparingTo("10000");
	}

	@Test
	void outroDonoNaoVe() {
		var id = criar(acao("PETR4", 1, 10)).texto("id");
		var outro = api.novoDono();

		assertThat(api.get("/api/investimentos/" + id, outro).status()).isEqualTo(404);
		assertThat(api.post("/api/investimentos/" + id + "/movimentacoes", Map.of("tipo", "RESGATE", "quantidade", 1),
				outro).status()).isEqualTo(404);
		assertThat(api.delete("/api/investimentos/" + id, outro).status()).isEqualTo(404);
		assertThat((List<?>) api.get("/api/investimentos", outro).objeto().get("posicoes")).isEmpty();
	}

	@Test
	void saldoPequenoDeCriptoNaoQuebra() {
		var r = criar(Map.of("tipo", "CRIPTO", "codigo", "pepe", "quantidade", 100, "preco", 0.00001234));

		assertThat(r.status()).isEqualTo(201);
		assertThat(n(r.objeto().get("quantidade"))).isEqualByComparingTo("100");
		assertThat(n(r.objeto().get("precoMedio"))).isEqualByComparingTo("0.00001234");
	}

	@Test
	void numerosForaDoLimiteSaoErroDeCampo() {
		var taxaMil = new HashMap<>(cdb());
		taxaMil.put("taxa", 1000);
		assertThat(erro(criar(taxaMil), "taxa")).isNotBlank();
		assertThat(erro(criar(acao("PETR4", 1_000_000_000_000L, 1)), "quantidade")).isNotBlank();
		assertThat(erro(criar(acao("PETR4", 1_000_000, 100_000)), "preco")).isNotBlank();
		assertThat(erro(criar(Map.of("tipo", "TESOURO", "tesouroTipo", "SELIC", "tesouroAno", HOJE.getYear() + 1,
				"quantidade", 1, "valor", 10_000_000_000L)), "valor")).isNotBlank();

		var id = criar(acao("PETR4", 1, 10)).texto("id");
		assertThat(erro(movimentar(id, Map.of("tipo", "APORTE", "quantidade", 1_000_000, "preco", 100_000)), "preco"))
			.isNotBlank();
	}

	@Test
	void resgatePorQuantidadeNaoTemValor() {
		var id = criar(acao("PETR4", 10, 10)).texto("id");
		var r = movimentar(id, Map.of("tipo", "RESGATE", "quantidade", 5));

		assertThat(movimentacoes(r)).filteredOn(m -> m.get("tipo").equals("RESGATE"))
			.singleElement()
			.satisfies(m -> assertThat(m.get("valor")).isNull());
	}

	@Test
	void apagarAContaLevaACarteira() {
		var uid = java.util.UUID.fromString(api.conta(dono).texto("id"));
		criar(acao("PETR4", 1, 10));
		assertThat(jdbc.queryForObject("select count(*) from investimento where usuario_id = ?", Integer.class, uid))
			.isEqualTo(1);

		assertThat(api.delete("/api/conta", Map.of("senha", ClienteTeste.SENHA), dono).status()).isEqualTo(204);

		assertThat(jdbc.queryForObject("select count(*) from investimento where usuario_id = ?", Integer.class, uid))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from movimentacao_investimento where usuario_id = ?",
				Integer.class, uid)).isZero();
	}

}
