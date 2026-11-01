package br.com.contadinho.gastos;

import static org.assertj.core.api.Assertions.assertThat;

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
 * HU-12, HU-46 e RN-26 contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GastosTests {

	private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

	private static final YearMonth ATUAL = YearMonth.from(HOJE);

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String mercado;

	private String cartao;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		mercado = api.get("/api/categorias", dono).lista().stream()
			.filter(c -> c.get("nome").equals("Mercado"))
			.map(c -> (String) c.get("id"))
			.findFirst()
			.orElseThrow();
		cartao = api.post("/api/cartoes", Map.of("banco", "INTER", "ultimos4", "9453", "diaFechamento", 28,
				"diaVencimento", 8), dono).texto("id");
	}

	private Map<String, Object> pedido(String descricao, Object valor, LocalDate data, String forma, String cartaoId) {
		var corpo = new HashMap<String, Object>(Map.of("descricao", descricao, "valor", valor, "categoriaId", mercado,
				"formaPagamento", forma));
		corpo.put("data", data == null ? null : data.toString());
		corpo.put("cartaoId", cartaoId);
		return corpo;
	}

	private ClienteTeste.Resposta registrar(Map<String, Object> corpo) {
		return api.post("/api/gastos", corpo, dono);
	}

	private Map<String, Object> mes(YearMonth mes) {
		return api.get("/api/gastos/mes/" + mes, dono).objeto();
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> gastos(YearMonth mes) {
		return (List<Map<String, Object>>) mes(mes).get("gastos");
	}

	@Test
	void registraComHojeEPadraoDaCategoria() {
		var criado = registrar(pedido("Feira", 86, null, "PIX_BOLETO", null));

		assertThat(criado.status()).isEqualTo(201);
		assertThat(criado.objeto()).containsEntry("data", HOJE.toString()).containsEntry("status", "PAGO")
			.containsEntry("valor", 86.0);
		assertThat(criado.objeto().get("essencial")).isNull();
		registrar(pedido("Padaria", 12.5, HOJE, "DEBITO", null));
		assertThat(mes(ATUAL)).containsEntry("total", 98.5).containsEntry("noCartao", 0);
		assertThat(gastos(ATUAL)).hasSize(2);
		assertThat(gastos(ATUAL.minusMonths(1))).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void recusaDadosInvalidos() {
		var futura = registrar(pedido("Feira", 86, HOJE.plusDays(1), "PIX_BOLETO", null));
		var semCartao = registrar(pedido("Feira", 86, HOJE, "CARTAO", null));
		var zero = registrar(pedido("Feira", 0, HOJE, "PIX_BOLETO", null));
		var corpo = pedido("Feira", 86, HOJE, "PIX_BOLETO", null);
		corpo.put("categoriaId", api.get("/api/categorias", api.novoDono()).lista().get(0).get("id"));
		var deOutro = registrar(corpo);

		assertThat((Map<String, Object>) futura.objeto().get("campos")).containsEntry("data",
				"A data não pode ser depois de hoje.");
		assertThat((Map<String, Object>) semCartao.objeto().get("campos")).containsEntry("cartaoId", "Escolha o cartão.");
		assertThat((Map<String, Object>) zero.objeto().get("campos")).containsEntry("valor",
				"Informe um valor maior que zero.");
		assertThat((Map<String, Object>) deOutro.objeto().get("campos")).containsEntry("categoriaId",
				"Escolha uma categoria da lista.");
		assertThat(gastos(ATUAL)).isEmpty();
	}

	@Test
	void noCartaoAguardaAFaturaENuncaSomaNoTotal() {
		var criado = registrar(pedido("Farmácia", 50, HOJE.minusDays(2), "CARTAO", cartao));

		assertThat(criado.objeto()).containsEntry("status", "AGUARDANDO_FATURA").containsEntry("cartaoId", cartao);
		assertThat(mes(YearMonth.from(HOJE.minusDays(2)))).containsEntry("total", 0).containsEntry("noCartao", 50.0);

		FaturaOfx.importar(api, dono, cartao, ATUAL.plusMonths(1),
				new FaturaOfx.Compra("f1", HOJE.minusDays(1), "50.40", "DROGARIA"));

		var ligado = gastos(YearMonth.from(HOJE.minusDays(2))).get(0);
		assertThat(ligado).containsEntry("status", "NA_FATURA").containsEntry("valor", 50.0);
		assertThat(ligado.get("compra")).isNotNull();
	}

	@Test
	void compraJaImportadaLigaAoRegistrar() {
		FaturaOfx.importar(api, dono, cartao, ATUAL.plusMonths(1),
				new FaturaOfx.Compra("f1", HOJE.minusDays(3), "120.00", "LOJA"));

		var criado = registrar(pedido("Tênis", 120, HOJE.minusDays(3), "CARTAO", cartao));

		assertThat(criado.objeto()).containsEntry("status", "NA_FATURA");
		// Um lançamento paga uma coisa só: o segundo gasto igual continua aguardando.
		assertThat(registrar(pedido("Tênis", 120, HOJE.minusDays(3), "CARTAO", cartao)).objeto())
			.containsEntry("status", "AGUARDANDO_FATURA");
	}

	@Test
	void contaFixaEscolheAntesDoGasto() {
		var conta = new HashMap<String, Object>(Map.of("descricao", "Streaming", "valor", 39.90, "categoriaId", mercado,
				"diaVencimento", HOJE.getDayOfMonth(), "formaPagamento", "CARTAO", "cartaoId", cartao));
		api.post("/api/contas-fixas", conta, dono);
		registrar(pedido("Streaming", 39.90, HOJE, "CARTAO", cartao));

		FaturaOfx.importar(api, dono, cartao, ATUAL.plusMonths(1),
				new FaturaOfx.Compra("s1", HOJE, "39.90", "STREAMING"));

		var contas = (List<?>) api.get("/api/contas-fixas/mes/" + ATUAL, dono).objeto().get("contas");
		assertThat(((Map<?, ?>) contas.get(0)).get("status")).isEqualTo("NA_FATURA");
		assertThat(gastos(ATUAL).get(0)).containsEntry("status", "AGUARDANDO_FATURA");
	}

	@Test
	void editarEExcluir() {
		var id = registrar(pedido("Farmácia", 50, HOJE, "CARTAO", cartao)).texto("id");
		FaturaOfx.importar(api, dono, cartao, ATUAL.plusMonths(1), new FaturaOfx.Compra("f1", HOJE, "50.00", "DROGARIA"));
		assertThat(gastos(ATUAL).get(0)).containsEntry("status", "NA_FATURA");

		var editado = api.put("/api/gastos/" + id, pedido("Remédio", 48.5, HOJE, "PIX_BOLETO", null), dono);

		assertThat(editado.status()).isEqualTo(200);
		assertThat(editado.objeto()).containsEntry("descricao", "Remédio").containsEntry("status", "PAGO")
			.containsEntry("valor", 48.5);
		assertThat(editado.objeto().get("cartaoId")).isNull();
		assertThat(mes(ATUAL)).containsEntry("total", 48.5);

		// De volta ao cartão, liga de novo; excluir não mexe no lançamento da fatura (HU-46.3).
		api.put("/api/gastos/" + id, pedido("Remédio", 50, HOJE, "CARTAO", cartao), dono);
		assertThat(gastos(ATUAL).get(0)).containsEntry("status", "NA_FATURA");
		assertThat(api.delete("/api/gastos/" + id, dono).status()).isEqualTo(204);
		assertThat(gastos(ATUAL)).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from lancamento where cartao_id = ?::uuid", Integer.class, cartao))
			.isEqualTo(1);
	}

	@Test
	void soODonoMexe() {
		var id = registrar(pedido("Feira", 86, HOJE, "PIX_BOLETO", null)).texto("id");
		var outro = api.novoDono();

		assertThat(api.put("/api/gastos/" + id, pedido("Feira", 90, HOJE, "PIX_BOLETO", null), outro).status())
			.isEqualTo(404);
		assertThat(api.delete("/api/gastos/" + id, outro).status()).isEqualTo(404);
		assertThat(api.get("/api/gastos/mes/" + ATUAL, outro).objeto().get("gastos")).isEqualTo(List.of());
	}

}
