package br.com.contadinho.avisos;

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
 * HU-28 (preferências dos avisos) contra um Postgres real. As contas vencem no último dia do mês e a fatura do
 * próximo mês fecha no dia 20 e vence no 28, então as datas olhadas sempre estão no futuro.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PreferenciasTests {

	private static final YearMonth PROXIMO = YearMonth.now(ZoneId.of("America/Sao_Paulo")).plusMonths(1);

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

	private static Map<String, Object> padroes() {
		var p = new HashMap<String, Object>();
		p.put("contaAtivo", true);
		p.put("contaAntecedencia", 2);
		p.put("faturaFechouAtivo", true);
		p.put("faturaReceberAtivo", true);
		p.put("faturaAntecedencia", 3);
		p.put("silencioAtivo", true);
		p.put("silencioInicio", "22:00");
		p.put("silencioFim", "08:00");
		return p;
	}

	private ClienteTeste.Resposta salvar(Map<String, Object> mudancas) {
		var corpo = padroes();
		corpo.putAll(mudancas);
		return api.put("/api/avisos/preferencias", corpo, dono);
	}

	private void cenario() {
		var categoria = api.get("/api/categorias", dono).lista().get(0).get("id");
		assertThat(api.post("/api/contas-fixas", Map.of("descricao", "Luz", "valor", 130, "categoriaId", categoria,
				"diaVencimento", 31, "formaPagamento", "PIX_BOLETO"), dono).status()).isEqualTo(201);
		var cartao = api.post("/api/cartoes", Map.of("banco", "INTER", "ultimos4", "9453", "diaFechamento", 20,
				"diaVencimento", 28), dono).texto("id");
		var lucas = api.post("/api/pessoas", Map.of("nome", "Lucas"), dono).texto("id");
		var compra = api.post("/api/cartoes/" + cartao + "/faturas/" + PROXIMO + "/lancamentos",
				Map.of("data", PROXIMO.minusMonths(1).atDay(5).toString(), "descricao", "Tênis", "valor", 120), dono)
			.texto("id");
		api.put("/api/lancamentos/" + compra + "/responsavel", new HashMap<>(Map.of("pessoaId", lucas, "proximas", false)),
				dono);
	}

	/** O aviso de cada tipo que cai no próximo mês. */
	private Map<String, Map<String, Object>> doProximoMes() {
		var porTipo = new HashMap<String, Map<String, Object>>();
		for (var a : api.get("/api/avisos/agenda", dono).lista()) {
			var quando = LocalDate.parse(((String) a.get("quando")).substring(0, 10));
			if (YearMonth.from(quando).equals(PROXIMO)) {
				porTipo.putIfAbsent((String) a.get("tipo"), a);
			}
		}
		return porTipo;
	}

	@Test
	void semNadaSalvoValemOsPadroes() {
		var lidas = api.get("/api/avisos/preferencias", dono);

		assertThat(lidas.status()).isEqualTo(200);
		assertThat(lidas.objeto()).containsAllEntriesOf(padroes());
	}

	@Test
	@SuppressWarnings("unchecked")
	void salvaERecusaAntecedenciaOuSilencioInvalidos() {
		var salvas = salvar(Map.of("contaAntecedencia", 3, "faturaReceberAtivo", false, "silencioInicio", "12:30"));

		assertThat(salvas.status()).isEqualTo(200);
		assertThat(api.get("/api/avisos/preferencias", dono).objeto()).containsEntry("contaAntecedencia", 3)
			.containsEntry("faturaReceberAtivo", false)
			.containsEntry("silencioInicio", "12:30");
		assertThat((Map<String, Object>) salvar(Map.of("contaAntecedencia", 4)).objeto().get("campos"))
			.containsEntry("contaAntecedencia", "Escolha 1, 2 ou 3 dias.");
		assertThat((Map<String, Object>) salvar(Map.of("faturaAntecedencia", 1)).objeto().get("campos"))
			.containsEntry("faturaAntecedencia", "Escolha 2, 3 ou 5 dias.");
		assertThat((Map<String, Object>) salvar(Map.of("silencioInicio", "08:00")).objeto().get("campos"))
			.containsEntry("silencioFim", "O início e o fim do silêncio precisam ser diferentes.");
		// Cada dono tem as suas.
		assertThat(api.get("/api/avisos/preferencias", api.novoDono()).objeto()).containsEntry("contaAntecedencia", 2);
	}

	@Test
	void aAgendaSegueAsPreferencias() {
		cenario();
		assertThat(doProximoMes()).containsKeys("CONTA_ANTES", "CONTA_NO_DIA", "FATURA_FECHOU", "FATURA_A_RECEBER");

		salvar(Map.of("contaAntecedencia", 1, "faturaAntecedencia", 5));

		var avisos = doProximoMes();
		assertThat(avisos.get("CONTA_ANTES")).containsEntry("titulo", "Luz vence amanhã")
			.containsEntry("quando", PROXIMO.atEndOfMonth().minusDays(1) + "T09:00:00");
		assertThat(avisos.get("FATURA_A_RECEBER")).containsEntry("titulo", "Fatura Inter •• 9453 vence em 5 dias")
			.containsEntry("quando", PROXIMO.atDay(23) + "T09:00:00");

		salvar(Map.of("contaAtivo", false, "faturaFechouAtivo", false));

		assertThat(doProximoMes()).containsOnlyKeys("FATURA_A_RECEBER");

		salvar(Map.of("faturaReceberAtivo", false, "faturaFechouAtivo", false, "contaAtivo", false));

		assertThat(api.get("/api/avisos/agenda", dono).lista()).isEqualTo(List.of());
	}

	@Test
	void avisoNoSilencioVaiParaOFimDele() {
		cenario();

		// Dentro do dia: das 8h às 10h, o aviso das 9h sai às 10h.
		salvar(Map.of("silencioInicio", "08:00", "silencioFim", "10:00"));
		assertThat(doProximoMes().get("CONTA_NO_DIA")).containsEntry("quando", PROXIMO.atEndOfMonth() + "T10:00:00");

		// Virando a noite: das 21h às 9h30, também no mesmo dia, às 9h30.
		salvar(Map.of("silencioInicio", "21:00", "silencioFim", "09:30"));
		assertThat(doProximoMes().get("CONTA_NO_DIA")).containsEntry("quando", PROXIMO.atEndOfMonth() + "T09:30:00");

		// Desligado, fica às 9h.
		salvar(Map.of("silencioAtivo", false, "silencioInicio", "08:00", "silencioFim", "10:00"));
		assertThat(doProximoMes().get("CONTA_NO_DIA")).containsEntry("quando", PROXIMO.atEndOfMonth() + "T09:00:00");
	}

}
