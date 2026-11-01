package br.com.contadinho.resumo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
 * HU-23 (resumo do mês) contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ResumoTests {

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

	private Map<String, Object> resumo(YearMonth mes, String quem) {
		var resposta = api.get("/api/resumo/mes/" + mes, quem);
		assertThat(resposta.status()).isEqualTo(200);
		return resposta.objeto();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> parte(Map<String, Object> resumo, String nome) {
		return (Map<String, Object>) resumo.get(nome);
	}

	private String cartao(String ultimos4, int diaVencimento) {
		return api.post("/api/cartoes", Map.of("banco", "INTER", "ultimos4", ultimos4, "diaFechamento", 20,
				"diaVencimento", diaVencimento), dono).texto("id");
	}

	private String compra(String cartaoId, YearMonth fatura, String descricao, double valor) {
		var corpo = Map.<String, Object>of("data", fatura.minusMonths(1).atDay(5).toString(), "descricao", descricao,
				"valor", valor);
		var criada = api.post("/api/cartoes/" + cartaoId + "/faturas/" + fatura + "/lancamentos", corpo, dono);
		assertThat(criada.status()).isEqualTo(201);
		return criada.texto("id");
	}

	/** Dia 31 vence no último dia do mês, então a conta sempre aparece neste mês. */
	private String contaFixa(String descricao, double valor, String forma, String cartaoId) {
		var corpo = new HashMap<String, Object>(Map.of("descricao", descricao, "valor", valor, "categoriaId", categoria,
				"diaVencimento", 31, "formaPagamento", forma));
		corpo.put("cartaoId", cartaoId);
		var criada = api.post("/api/contas-fixas", corpo, dono);
		assertThat(criada.status()).isEqualTo(201);
		return criada.texto("id");
	}

	@SuppressWarnings("unchecked")
	private String ocorrencia(String contaId) {
		var contas = (List<Map<String, Object>>) api.get("/api/contas-fixas/mes/" + ATUAL, dono).objeto().get("contas");
		return contas.stream()
			.filter(c -> contaId.equals(((Map<String, Object>) c.get("conta")).get("id")))
			.map(c -> (String) c.get("id"))
			.findFirst()
			.orElseThrow();
	}

	private void gasto(String descricao, double valor, String forma, String cartaoId) {
		var corpo = new HashMap<String, Object>(Map.of("descricao", descricao, "valor", valor, "categoriaId", categoria,
				"formaPagamento", forma, "data", HOJE.toString()));
		corpo.put("cartaoId", cartaoId);
		assertThat(api.post("/api/gastos", corpo, dono).status()).isEqualTo(201);
	}

	private void receita(String descricao, double valor, LocalDate data, boolean recorrente) {
		assertThat(api.post("/api/receitas",
				Map.of("descricao", descricao, "valor", valor, "data", data.toString(), "recorrente", recorrente), dono)
			.status()).isEqualTo(201);
	}

	@Test
	@SuppressWarnings("unchecked")
	void somaReceitasDespesasESaldo() {
		receita("Freela", 1200, HOJE, false);
		receita("Salário", 7800, ATUAL.atDay(1), true);

		var luz = contaFixa("Luz", 130, "PIX_BOLETO", null);
		contaFixa("Internet", 100, "PIX_BOLETO", null);
		var inter = cartao("9453", 1);
		var nubank = cartao("1234", 28);
		contaFixa("Streaming", 39.90, "CARTAO", inter);
		assertThat(api.post("/api/contas-fixas/ocorrencias/" + ocorrencia(luz) + "/pagamento",
				Map.of("valorPago", 142.37), dono).status()).isEqualTo(200);

		gasto("Feira", 86, "PIX_BOLETO", null);
		gasto("Farmácia", 50, "CARTAO", nubank);

		// A compra do Lucas sai da minha parte; a fatura de outro mês fica de fora.
		var lucas = api.post("/api/pessoas", Map.of("nome", "Lucas"), dono).texto("id");
		compra(inter, ATUAL, "Mercado", 300);
		var doLucas = compra(inter, ATUAL, "Tênis", 120);
		var responsavel = new HashMap<String, Object>(Map.of("pessoaId", lucas, "proximas", false));
		assertThat(api.put("/api/lancamentos/" + doLucas + "/responsavel", responsavel, dono).status()).isEqualTo(200);
		compra(nubank, ATUAL, "Posto", 200);
		compra(nubank, ATUAL.minusMonths(1), "Posto", 999);

		var resumo = resumo(ATUAL, dono);

		assertThat(parte(resumo, "receitas")).containsEntry("total", 9000.0).containsEntry("recebido", 1200.0)
			.containsEntry("previsto", 7800.0);
		var despesas = parte(resumo, "despesas");
		assertThat(despesas).containsEntry("total", 828.37).containsEntry("faturas", 500.0)
			.containsEntry("contasFixas", 242.37).containsEntry("avulsos", 86.0);
		assertThat(resumo).containsEntry("saldo", 8171.63);

		var faturas = (List<Map<String, Object>>) resumo.get("faturas");
		assertThat(faturas).extracting(f -> f.get("cartao")).containsExactly("Inter •• 9453", "Inter •• 1234");
		assertThat(faturas.get(0)).containsEntry("total", 420.0).containsEntry("minhaParte", 300.0)
			.containsEntry("vencimento", ATUAL.atDay(1).toString());

		// Fatura vencida conta como paga; a que vence hoje ou depois, como a pagar.
		var aPagar = new BigDecimal("100.00");
		for (var f : faturas) {
			if (!LocalDate.parse((String) f.get("vencimento")).isBefore(HOJE)) {
				aPagar = aPagar.add(BigDecimal.valueOf((Double) f.get("minhaParte")));
			}
		}
		assertThat(despesas).containsEntry("aPagar", aPagar.doubleValue())
			.containsEntry("pago", new BigDecimal("828.37").subtract(aPagar).doubleValue());
	}

	@Test
	void cadaMesECadaDonoTemOSeu() {
		receita("Salário", 7800, ATUAL.atDay(1), true);
		receita("Freela", 1200, HOJE, false);
		contaFixa("Internet", 100, "PIX_BOLETO", null);
		var inter = cartao("9453", 8);
		compra(inter, ATUAL.plusMonths(1), "Mercado", 300);

		var proximo = resumo(ATUAL.plusMonths(1), dono);

		assertThat(parte(proximo, "receitas")).containsEntry("total", 7800.0).containsEntry("recebido", 0);
		assertThat(parte(proximo, "despesas")).containsEntry("total", 400.0).containsEntry("aPagar", 400.0)
			.containsEntry("pago", 0.0);
		assertThat(proximo).containsEntry("saldo", 7400.0);

		var outro = resumo(ATUAL, api.novoDono());
		assertThat(outro).containsEntry("saldo", 0).containsEntry("faturas", List.of());
		assertThat(parte(outro, "despesas")).containsEntry("total", 0);
		assertThat(parte(outro, "receitas")).containsEntry("total", 0);
	}

}
