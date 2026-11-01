package br.com.contadinho.gastos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDate;
import java.time.Month;
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
 * HU-11, HU-15.1, RN-21, RN-22 e RN-57 contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContasFixasTests {

	private static final YearMonth ATUAL = YearMonth.now(ZoneId.of("America/Sao_Paulo"));

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String moradia;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		moradia = categoria("Moradia");
	}

	private String categoria(String nome) {
		return api.get("/api/categorias", dono).lista().stream()
			.filter(c -> c.get("nome").equals(nome))
			.map(c -> (String) c.get("id"))
			.findFirst()
			.orElseThrow();
	}

	private Map<String, Object> pedido(String descricao, Object valor, int dia, String forma, String cartaoId) {
		var corpo = new HashMap<String, Object>(Map.of("descricao", descricao, "valor", valor, "categoriaId", moradia,
				"diaVencimento", dia, "formaPagamento", forma));
		corpo.put("cartaoId", cartaoId);
		return corpo;
	}

	private String cadastrar(String descricao, Object valor, int dia) {
		var criada = api.post("/api/contas-fixas", pedido(descricao, valor, dia, "PIX_BOLETO", null), dono);
		assertThat(criada.status()).isEqualTo(201);
		return criada.texto("id");
	}

	private Map<String, Object> mes(YearMonth mes) {
		return api.get("/api/contas-fixas/mes/" + mes, dono).objeto();
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> contas(YearMonth mes) {
		return (List<Map<String, Object>>) mes(mes).get("contas");
	}

	@Test
	void contaNovaTemAsCategoriasPadrao() {
		var categorias = api.get("/api/categorias", dono).lista();

		assertThat(categorias).extracting(c -> c.get("nome")).containsExactlyInAnyOrder("Moradia", "Educação",
				"Saúde", "Contas", "Mercado", "Transporte", "Delivery", "Lazer", "Compras", "Assinaturas", "Viagem",
				"Outros");
		assertThat(categorias).filteredOn(c -> c.get("nome").equals("Lazer")).first()
			.satisfies(c -> assertThat(c).containsEntry("essencial", false).containsEntry("arquivada", false));
	}

	@Test
	void geraUmaOcorrenciaPorMesDesdeOAtual() {
		// Dia 31 vence no último dia do mês: ainda não passou, então começa neste mês.
		cadastrar("Aluguel", 1900, 31);

		var doMes = mes(ATUAL);

		assertThat(doMes).containsEntry("total", 1900.0).containsEntry("aPagar", 1900.0).containsEntry("pago", 0);
		var aluguel = contas(ATUAL).get(0);
		assertThat(aluguel).containsEntry("status", "A_PAGAR").containsEntry("valor", 1900.0)
			.containsEntry("vencimento", ATUAL.atEndOfMonth().toString());
		assertThat(contas(ATUAL.plusMonths(1))).hasSize(1);
		assertThat(contas(ATUAL.minusMonths(1))).isEmpty();
		// Abrir o mês de novo não duplica.
		assertThat(contas(ATUAL)).hasSize(1);
	}

	@Test
	void vencimentoQueJaPassouComecaNoProximoMes() {
		assumeTrue(LocalDate.now(ZoneId.of("America/Sao_Paulo")).getDayOfMonth() > 1, "no dia 1 nada passou");

		var id = cadastrar("Aluguel", 1900, 1);

		assertThat(contas(ATUAL)).isEmpty();
		assertThat(contas(ATUAL.plusMonths(1)).get(0)).containsEntry("vencimento", ATUAL.plusMonths(1).atDay(1).toString());
		// Mudar o valor antes de a conta começar vale desde o primeiro mês dela.
		api.put("/api/contas-fixas/" + id, pedido("Aluguel", 2000, 1, "PIX_BOLETO", null), dono);
		assertThat(contas(ATUAL.plusMonths(1)).get(0)).containsEntry("valor", 2000.0);
	}

	@Test
	void dia31NoMesCurtoViraOUltimoDia() {
		cadastrar("Condomínio", 600, 31);
		var fevereiro = ATUAL.withMonth(Month.FEBRUARY.getValue());
		if (fevereiro.isBefore(ATUAL)) {
			fevereiro = fevereiro.plusYears(1);
		}

		assertThat(contas(fevereiro).get(0)).containsEntry("vencimento", fevereiro.atEndOfMonth().toString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void noCartaoPedeOCartaoEAguardaAFatura() {
		var semCartao = api.post("/api/contas-fixas", pedido("Internet", 120, 15, "CARTAO", null), dono);

		assertThat(semCartao.status()).isEqualTo(400);
		assertThat((Map<String, Object>) semCartao.objeto().get("campos")).containsEntry("cartaoId", "Escolha o cartão.");

		var cartao = api.post("/api/cartoes", Map.of("apelido", "Inter", "banco", "INTER", "ultimos4", "9453",
				"diaFechamento", 5, "diaVencimento", 12), dono).texto("id");
		api.post("/api/contas-fixas", pedido("Internet", 120, 31, "CARTAO", cartao), dono);

		assertThat(contas(ATUAL).get(0)).containsEntry("status", "AGUARDANDO_FATURA");
		assertThat(mes(ATUAL)).containsEntry("noCartao", 120.0).containsEntry("aPagar", 0);
	}

	@Test
	void mudarOValorValeDoMesAtualEmDiante() {
		var id = cadastrar("Escola", 1250, 10);
		// A conta já existia no mês passado, com o mesmo valor.
		jdbc.update("update conta_fixa set inicio = ?::date where id = ?::uuid", ATUAL.minusMonths(1).atDay(1).toString(), id);
		jdbc.update("update conta_fixa_valor set vigente_desde = ?::date where conta_fixa_id = ?::uuid",
				ATUAL.minusMonths(1).atDay(1).toString(), id);
		contas(ATUAL.minusMonths(1));
		contas(ATUAL.plusMonths(1));

		var alterada = api.put("/api/contas-fixas/" + id, pedido("Escola", 1300, 12, "PIX_BOLETO", null), dono);

		assertThat(alterada.status()).isEqualTo(200);
		assertThat(contas(ATUAL.minusMonths(1)).get(0)).containsEntry("valor", 1250.0)
			.containsEntry("vencimento", ATUAL.minusMonths(1).atDay(10).toString());
		assertThat(contas(ATUAL).get(0)).containsEntry("valor", 1300.0)
			.containsEntry("vencimento", ATUAL.atDay(12).toString());
		assertThat(contas(ATUAL.plusMonths(1)).get(0)).containsEntry("valor", 1300.0);
		assertThat(contas(ATUAL.plusMonths(2)).get(0)).containsEntry("valor", 1300.0);
		assertThat(contas(ATUAL.minusMonths(1)).get(0)).containsEntry("valorAtual", 1300.0);
	}

	@Test
	void encerrarParaNoMesSeguinteEMantemOHistorico() {
		var id = cadastrar("Academia", 99.9, 31);
		contas(ATUAL.plusMonths(1));

		var encerrada = api.post("/api/contas-fixas/" + id + "/encerrar", null, dono);

		assertThat(encerrada.status()).isEqualTo(200);
		assertThat(encerrada.objeto().get("encerradaEm")).isNotNull();
		assertThat(contas(ATUAL)).hasSize(1);
		assertThat(contas(ATUAL.plusMonths(1))).isEmpty();
		assertThat(api.put("/api/contas-fixas/" + id, pedido("Academia", 120, 8, "PIX_BOLETO", null), dono).status())
			.isEqualTo(409);
	}

	@Test
	@SuppressWarnings("unchecked")
	void categoriaECartaoSoDoDono() {
		var outro = api.novoDono();
		var deOutro = api.get("/api/categorias", outro).lista().get(0).get("id");
		var corpo = pedido("Luz", 130, 18, "PIX_BOLETO", null);
		corpo.put("categoriaId", deOutro);

		var recusada = api.post("/api/contas-fixas", corpo, dono);

		assertThat((Map<String, Object>) recusada.objeto().get("campos")).containsEntry("categoriaId",
				"Escolha uma categoria da lista.");
		var id = cadastrar("Luz", 130, 18);
		assertThat(api.post("/api/contas-fixas/" + id + "/encerrar", null, outro).status()).isEqualTo(404);
		assertThat(api.get("/api/contas-fixas/mes/" + ATUAL, outro).objeto().get("contas")).isEqualTo(List.of());
	}

}
