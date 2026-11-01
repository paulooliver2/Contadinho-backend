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
 * HU-13, RN-24 e RN-25 contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PagarContaTests {

	private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

	private static final YearMonth ATUAL = YearMonth.from(HOJE);

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
		moradia = api.get("/api/categorias", dono).lista().stream()
			.filter(c -> c.get("nome").equals("Moradia"))
			.map(c -> (String) c.get("id"))
			.findFirst()
			.orElseThrow();
	}

	/** Dia 31 vence no último dia do mês, então a conta sempre começa neste mês. */
	private String ocorrencia(String forma, String cartaoId) {
		var corpo = new HashMap<String, Object>(Map.of("descricao", "Luz", "valor", 130, "categoriaId", moradia,
				"diaVencimento", 31, "formaPagamento", forma));
		corpo.put("cartaoId", cartaoId);
		assertThat(api.post("/api/contas-fixas", corpo, dono).status()).isEqualTo(201);
		return (String) contas().get(0).get("id");
	}

	private Map<String, Object> mes() {
		return api.get("/api/contas-fixas/mes/" + ATUAL, dono).objeto();
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> contas() {
		return (List<Map<String, Object>>) mes().get("contas");
	}

	private String pagamento(String id) {
		return "/api/contas-fixas/ocorrencias/" + id + "/pagamento";
	}

	@Test
	void pagarSemDadosUsaHojeEOPrevisto() {
		var id = ocorrencia("PIX_BOLETO", null);

		var paga = api.post(pagamento(id), Map.of(), dono);

		assertThat(paga.status()).isEqualTo(200);
		assertThat(paga.objeto()).containsEntry("status", "PAGA").containsEntry("pagoEm", HOJE.toString())
			.containsEntry("valorPago", 130.0).containsEntry("valor", 130.0);
		assertThat(mes()).containsEntry("pago", 130.0).containsEntry("aPagar", 0);
	}

	@Test
	void valorPagoDiferenteEntraNoTotal() {
		var id = ocorrencia("PIX_BOLETO", null);

		api.post(pagamento(id), Map.of("pagoEm", HOJE.minusDays(1).toString(), "valorPago", 142.37), dono);

		assertThat(contas().get(0)).containsEntry("valor", 142.37).containsEntry("valorPrevisto", 130.0)
			.containsEntry("pagoEm", HOJE.minusDays(1).toString());
		assertThat(mes()).containsEntry("total", 142.37).containsEntry("pago", 142.37);
	}

	@Test
	@SuppressWarnings("unchecked")
	void dataDepoisDeHojeEValorZeroSaoRecusados() {
		var id = ocorrencia("PIX_BOLETO", null);

		var futura = api.post(pagamento(id), Map.of("pagoEm", HOJE.plusDays(1).toString()), dono);
		var zero = api.post(pagamento(id), Map.of("valorPago", 0), dono);

		assertThat(futura.status()).isEqualTo(400);
		assertThat((Map<String, Object>) futura.objeto().get("campos")).containsEntry("pagoEm",
				"A data não pode ser depois de hoje.");
		assertThat((Map<String, Object>) zero.objeto().get("campos")).containsEntry("valorPago",
				"Informe um valor maior que zero.");
		assertThat(contas().get(0)).containsEntry("status", "A_PAGAR");
	}

	@Test
	void desfazerVoltaAFicarAPagar() {
		var id = ocorrencia("PIX_BOLETO", null);
		api.post(pagamento(id), Map.of("valorPago", 150), dono);

		var desfeita = api.delete(pagamento(id), dono);

		assertThat(desfeita.status()).isEqualTo(200);
		assertThat(desfeita.objeto()).containsEntry("status", "A_PAGAR").containsEntry("valor", 130.0);
		assertThat(desfeita.objeto().get("pagoEm")).isNull();
		assertThat(desfeita.objeto().get("valorPago")).isNull();
		assertThat(mes()).containsEntry("aPagar", 130.0);
	}

	@Test
	void contaNoCartaoEPagaPelaFatura() {
		var cartao = api.post("/api/cartoes", Map.of("apelido", "Inter", "banco", "INTER", "ultimos4", "9453",
				"diaFechamento", 5, "diaVencimento", 12), dono).texto("id");
		var id = ocorrencia("CARTAO", cartao);

		var recusada = api.post(pagamento(id), Map.of(), dono);

		assertThat(recusada.status()).isEqualTo(409);
		assertThat(recusada.texto("detail")).isEqualTo("Conta no cartão é paga pela fatura.");
	}

	@Test
	void debitoAutomaticoEPagoNoVencimentoEDesfazerNaoMarcaDeNovo() {
		var id = ocorrencia("DEBITO_AUTOMATICO", null);
		// Ainda não venceu: fica a pagar. No último dia do mês a leitura acima já pagou; volta ao começo.
		jdbc.update("update ocorrencia_conta set vencimento = ?::date, pago_em = null, valor_pago = null where id = ?::uuid",
				HOJE.plusDays(1).toString(), id);
		assertThat(contas().get(0)).containsEntry("status", "A_PAGAR");

		jdbc.update("update ocorrencia_conta set vencimento = ?::date where id = ?::uuid", HOJE.toString(), id);

		assertThat(contas().get(0)).containsEntry("status", "PAGA").containsEntry("pagoEm", HOJE.toString())
			.containsEntry("valorPago", 130.0);
		api.delete(pagamento(id), dono);
		assertThat(contas().get(0)).containsEntry("status", "A_PAGAR");
		// Pagar à mão continua valendo.
		api.post(pagamento(id), Map.of(), dono);
		assertThat(contas().get(0)).containsEntry("status", "PAGA");
	}

	@Test
	void soODonoPaga() {
		var id = ocorrencia("PIX_BOLETO", null);
		var outro = api.novoDono();

		assertThat(api.post(pagamento(id), Map.of(), outro).status()).isEqualTo(404);
		assertThat(api.delete(pagamento(id), outro).status()).isEqualTo(404);
		assertThat(contas().get(0)).containsEntry("status", "A_PAGAR");
	}

}
