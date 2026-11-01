package br.com.contadinho.receitas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
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
 * HU-22 e HU-46 (receitas) contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReceitasTests {

	private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

	private static final YearMonth ATUAL = YearMonth.from(HOJE);

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

	private static Map<String, Object> pedido(String descricao, Object valor, LocalDate data, boolean recorrente) {
		return Map.of("descricao", descricao, "valor", valor, "data", data.toString(), "recorrente", recorrente);
	}

	private String cadastrar(String descricao, Object valor, LocalDate data, boolean recorrente) {
		var criada = api.post("/api/receitas", pedido(descricao, valor, data, recorrente), dono);
		assertThat(criada.status()).isEqualTo(201);
		return criada.texto("id");
	}

	private Map<String, Object> mes(YearMonth mes) {
		return api.get("/api/receitas/mes/" + mes, dono).objeto();
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> receitas(YearMonth mes) {
		return (List<Map<String, Object>>) mes(mes).get("receitas");
	}

	@Test
	void deUmaVezQueJaCaiuNasceRecebida() {
		cadastrar("Freela de design", 1200, HOJE, false);

		assertThat(receitas(ATUAL)).singleElement()
			.satisfies(r -> assertThat(r).containsEntry("status", "RECEBIDA").containsEntry("valor", 1200.0)
				.containsEntry("data", HOJE.toString()));
		assertThat(mes(ATUAL)).containsEntry("total", 1200.0).containsEntry("recebido", 1200.0)
			.containsEntry("previsto", 0);
		assertThat(receitas(ATUAL.plusMonths(1))).isEmpty();
	}

	@Test
	void deUmaVezFuturaFicaPrevista() {
		cadastrar("Restituição", 900, HOJE.plusMonths(1), false);

		assertThat(receitas(ATUAL)).isEmpty();
		assertThat(receitas(ATUAL.plusMonths(1)).get(0)).containsEntry("status", "PREVISTA");
		assertThat(mes(ATUAL.plusMonths(1))).containsEntry("previsto", 900.0);
	}

	@Test
	void deTodoMesGeraUmaPorMesEEsperaConfirmar() {
		cadastrar("Salário", 7800, ATUAL.atDay(1), true);

		assertThat(receitas(ATUAL).get(0)).containsEntry("status", "A_CONFIRMAR")
			.containsEntry("data", ATUAL.atDay(1).toString());
		assertThat(receitas(ATUAL.plusMonths(1)).get(0)).containsEntry("status", "PREVISTA");
		assertThat(receitas(ATUAL.minusMonths(1))).isEmpty();
		// Abrir o mês de novo não duplica.
		assertThat(receitas(ATUAL)).hasSize(1);
	}

	@Test
	void confirmarComOutroValorEDesfazer() {
		cadastrar("Salário", 7800, ATUAL.atDay(1), true);
		var id = (String) receitas(ATUAL).get(0).get("id");

		var confirmada = api.post("/api/receitas/ocorrencias/" + id + "/confirmacao", Map.of("valorRecebido", 7912.45),
				dono);

		assertThat(confirmada.status()).isEqualTo(200);
		assertThat(confirmada.objeto()).containsEntry("status", "RECEBIDA").containsEntry("valor", 7912.45)
			.containsEntry("valorPrevisto", 7800.0);
		assertThat(mes(ATUAL)).containsEntry("total", 7912.45).containsEntry("recebido", 7912.45);
		// O próximo mês continua com o valor do cadastro.
		assertThat(receitas(ATUAL.plusMonths(1)).get(0)).containsEntry("valor", 7800.0);

		var desfeita = api.delete("/api/receitas/ocorrencias/" + id + "/confirmacao", dono);

		assertThat(desfeita.objeto()).containsEntry("status", "A_CONFIRMAR").containsEntry("valor", 7800.0);
		assertThat(api.post("/api/receitas/ocorrencias/" + id + "/confirmacao", Map.of(), dono).objeto())
			.containsEntry("valor", 7800.0);
	}

	@Test
	void mudarATodoMesValeDoMesAtualEmDiante() {
		var id = cadastrar("Aluguel da vaga", 700, ATUAL.minusMonths(1).atDay(10), true);
		receitas(ATUAL.minusMonths(1));
		receitas(ATUAL.plusMonths(1));

		var alterada = api.put("/api/receitas/" + id, pedido("Aluguel da vaga", 750, ATUAL.atDay(12), true), dono);

		assertThat(alterada.status()).isEqualTo(200);
		assertThat(alterada.objeto()).containsEntry("primeiroMes", ATUAL.minusMonths(1).toString())
			.containsEntry("dia", 12);
		assertThat(receitas(ATUAL.minusMonths(1)).get(0)).containsEntry("valor", 700.0)
			.containsEntry("data", ATUAL.minusMonths(1).atDay(10).toString());
		assertThat(receitas(ATUAL).get(0)).containsEntry("valor", 750.0)
			.containsEntry("data", ATUAL.atDay(12).toString());
		assertThat(receitas(ATUAL.plusMonths(1)).get(0)).containsEntry("valor", 750.0);
	}

	@Test
	@SuppressWarnings("unchecked")
	void encerrarEExcluir() {
		var salario = cadastrar("Salário", 7800, ATUAL.atDay(5), true);
		var freela = cadastrar("Freela", 300, HOJE, false);

		var mudarRepeticao = api.put("/api/receitas/" + freela, pedido("Freela", 300, HOJE, true), dono);
		assertThat((Map<String, Object>) mudarRepeticao.objeto().get("campos")).containsEntry("recorrente",
				"Para mudar se repete, exclua e cadastre de novo.");
		assertThat(api.post("/api/receitas/" + freela + "/encerrar", null, dono).status()).isEqualTo(409);

		receitas(ATUAL.plusMonths(1));
		assertThat(api.post("/api/receitas/" + salario + "/encerrar", null, dono).status()).isEqualTo(200);

		assertThat(receitas(ATUAL)).hasSize(2);
		assertThat(receitas(ATUAL.plusMonths(1))).isEmpty();
		assertThat(api.put("/api/receitas/" + salario, pedido("Salário", 8000, ATUAL.atDay(5), true), dono).status())
			.isEqualTo(409);

		assertThat(api.delete("/api/receitas/" + freela, dono).status()).isEqualTo(204);
		assertThat(receitas(ATUAL)).extracting(r -> ((Map<String, Object>) r.get("receita")).get("descricao"))
			.containsExactly("Salário");
	}

	@Test
	void soODonoMexe() {
		var id = cadastrar("Salário", 7800, ATUAL.atDay(1), true);
		var ocorrencia = (String) receitas(ATUAL).get(0).get("id");
		var outro = api.novoDono();

		assertThat(api.put("/api/receitas/" + id, pedido("Salário", 1, HOJE, true), outro).status()).isEqualTo(404);
		assertThat(api.delete("/api/receitas/" + id, outro).status()).isEqualTo(404);
		assertThat(api.post("/api/receitas/ocorrencias/" + ocorrencia + "/confirmacao", Map.of(), outro).status())
			.isEqualTo(404);
		assertThat(api.get("/api/receitas/mes/" + ATUAL, outro).objeto().get("receitas")).isEqualTo(List.of());
	}

}
