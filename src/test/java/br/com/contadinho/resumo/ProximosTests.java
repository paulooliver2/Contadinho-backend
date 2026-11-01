package br.com.contadinho.resumo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDate;
import java.time.YearMonth;
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

/**
 * HU-24 (próximos vencimentos e pendências no Início) contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProximosTests {

	private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

	private static final YearMonth ATUAL = YearMonth.from(HOJE);

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String categoria;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		categoria = api.get("/api/categorias", dono).lista().stream()
			.filter(c -> c.get("nome").equals("Moradia"))
			.map(c -> (String) c.get("id"))
			.findFirst()
			.orElseThrow();
	}

	private Map<String, Object> proximos(String quem) {
		var resposta = api.get("/api/resumo/proximos", quem);
		assertThat(resposta.status()).isEqualTo(200);
		return resposta.objeto();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> parte(Map<String, Object> proximos, String nome) {
		return (Map<String, Object>) proximos.get(nome);
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> lista(Map<String, Object> mapa, String nome) {
		return (List<Map<String, Object>>) mapa.get(nome);
	}

	private void contaFixa(String descricao, double valor, int dia, String forma, String cartaoId) {
		var corpo = new HashMap<String, Object>(Map.of("descricao", descricao, "valor", valor, "categoriaId", categoria,
				"diaVencimento", dia, "formaPagamento", forma));
		corpo.put("cartaoId", cartaoId);
		assertThat(api.post("/api/contas-fixas", corpo, dono).status()).isEqualTo(201);
	}

	private String cartao(String ultimos4) {
		return api.post("/api/cartoes", Map.of("banco", "INTER", "ultimos4", ultimos4, "diaFechamento", 28,
				"diaVencimento", 8), dono).texto("id");
	}

	private String compra(String cartaoId, YearMonth fatura, String descricao, double valor, String pessoaId) {
		var corpo = Map.<String, Object>of("data", fatura.minusMonths(1).atDay(5).toString(), "descricao", descricao,
				"valor", valor);
		var id = api.post("/api/cartoes/" + cartaoId + "/faturas/" + fatura + "/lancamentos", corpo, dono).texto("id");
		if (pessoaId != null) {
			var responsavel = new HashMap<String, Object>(Map.of("pessoaId", pessoaId, "proximas", false));
			assertThat(api.put("/api/lancamentos/" + id + "/responsavel", responsavel, dono).status()).isEqualTo(200);
		}
		return id;
	}

	private String pessoa(String nome) {
		return api.post("/api/pessoas", Map.of("nome", nome), dono).texto("id");
	}

	@Test
	@SuppressWarnings("unchecked")
	void tresProximasContasForaDoCartao() {
		var inter = cartao("9453");
		// Dia 31 vence no último dia do mês, então todas começam neste mês.
		contaFixa("Luz", 130, 31, "PIX_BOLETO", null);
		contaFixa("Internet", 100, 31, "PIX_BOLETO", null);
		contaFixa("Gás", 60, 31, "PIX_BOLETO", null);
		contaFixa("Condomínio", 800, 31, "PIX_BOLETO", null);
		contaFixa("Streaming", 39.90, 31, "CARTAO", inter);

		var contas = parte(proximos(dono), "contas");

		assertThat(contas).containsEntry("fixosPorMes", 1129.9);
		assertThat(lista(contas, "proximas")).extracting(c -> c.get("descricao"))
			.containsExactly("Condomínio", "Gás", "Internet");
		assertThat(lista(contas, "proximas").get(0)).containsEntry("vencimento", ATUAL.atEndOfMonth().toString())
			.containsEntry("valor", 800.0);

		// Pagas, saem da lista e entram as do próximo mês.
		var doMes = (List<Map<String, Object>>) api.get("/api/contas-fixas/mes/" + ATUAL, dono).objeto().get("contas");
		doMes.stream()
			.filter(c -> !((Map<String, Object>) c.get("conta")).get("descricao").equals("Luz"))
			.filter(c -> c.get("status").equals("A_PAGAR"))
			.forEach(c -> api.post("/api/contas-fixas/ocorrencias/" + c.get("id") + "/pagamento", Map.of(), dono));

		var proximoFim = ATUAL.plusMonths(1).atEndOfMonth().toString();
		assertThat(lista(parte(proximos(dono), "contas"), "proximas")).extracting(c -> c.get("descricao"),
				c -> c.get("vencimento"))
			.containsExactly(tuple("Luz", ATUAL.atEndOfMonth().toString()), tuple("Condomínio", proximoFim),
					tuple("Gás", proximoFim));
	}

	@Test
	void quemAindaDeveEAFaturaAtualDeCadaCartao() {
		var inter = cartao("9453");
		var nubank = cartao("1234");
		// A próxima a vencer: a deste mês até o dia 8, depois a do mês seguinte.
		var mes = HOJE.getDayOfMonth() <= 8 ? ATUAL : ATUAL.plusMonths(1);
		var lucas = pessoa("Lucas");
		var ana = pessoa("Ana");
		var rafa = pessoa("Rafa");
		compra(inter, mes, "Mercado", 200, null);
		compra(inter, mes, "Tênis", 300, lucas);
		compra(inter, mes, "Livro", 100, ana);
		compra(inter, mes, "Cinema", 50, rafa);
		compra(inter, mes.plusMonths(1), "Viagem", 999, null);
		api.post("/api/receber/pessoas/" + ana + "/pagamentos", Map.of("valor", 40), dono);
		api.post("/api/receber/pessoas/" + rafa + "/pagamentos", Map.of("valor", 50), dono);

		var proximos = proximos(dono);

		var aReceber = parte(proximos, "aReceber");
		assertThat(aReceber).containsEntry("total", 360.0).containsEntry("original", 400.0);
		assertThat(lista(aReceber, "devedores")).extracting(d -> d.get("pessoaId"), d -> d.get("saldo"),
				d -> d.get("pagouParte"))
			.containsExactlyInAnyOrder(tuple(lucas, 300.0, false),
					tuple(ana, 60.0, true));

		var faturas = lista(proximos, "faturas");
		assertThat(faturas).extracting(f -> f.get("cartao")).containsExactly("Inter •• 9453", "Inter •• 1234");
		var fechada = mes.minusMonths(1).atDay(28).isBefore(HOJE);
		assertThat(faturas.get(0)).containsEntry("mes", mes.toString())
			.containsEntry("vencimento", mes.atDay(8).toString())
			.containsEntry("total", 650.0)
			.containsEntry("fechada", fechada);
		assertThat(lista(faturas.get(0), "partes")).extracting(p -> p.get("pessoaId"), p -> p.get("total"))
			.containsExactly(tuple(null, 200.0),
					tuple(lucas, 300.0), tuple(ana, 100.0),
					tuple(rafa, 50.0));
		assertThat(faturas.get(1)).containsEntry("total", 0).containsEntry("partes", List.of());
	}

	@Test
	void outroDonoNaoVeNada() {
		contaFixa("Luz", 130, 31, "PIX_BOLETO", null);
		cartao("9453");

		var outro = proximos(api.novoDono());

		assertThat(lista(parte(outro, "contas"), "proximas")).isEmpty();
		assertThat(parte(outro, "contas")).containsEntry("fixosPorMes", 0);
		assertThat(parte(outro, "aReceber")).containsEntry("total", 0).containsEntry("devedores", List.of());
		assertThat(outro).containsEntry("faturas", List.of());
	}

}
