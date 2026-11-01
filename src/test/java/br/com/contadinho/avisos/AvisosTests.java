package br.com.contadinho.avisos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * HU-26 e HU-29 (agenda dos avisos de conta fixa) contra um Postgres real. As contas vencem no dia 31, isto é, no
 * último dia do mês, então sempre há uma deste mês e uma do próximo.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AvisosTests {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private static final YearMonth ATUAL = YearMonth.now(FUSO);

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

	private String contaFixa(String descricao, double valor, String forma, String cartaoId) {
		var corpo = new HashMap<String, Object>(Map.of("descricao", descricao, "valor", valor, "categoriaId", categoria,
				"diaVencimento", 31, "formaPagamento", forma));
		corpo.put("cartaoId", cartaoId);
		var criada = api.post("/api/contas-fixas", corpo, dono);
		assertThat(criada.status()).isEqualTo(201);
		return criada.texto("id");
	}

	@SuppressWarnings("unchecked")
	private String ocorrencia(String contaId, YearMonth mes) {
		var contas = (List<Map<String, Object>>) api.get("/api/contas-fixas/mes/" + mes, dono).objeto().get("contas");
		return contas.stream()
			.filter(c -> contaId.equals(((Map<String, Object>) c.get("conta")).get("id")))
			.map(c -> (String) c.get("id"))
			.findFirst()
			.orElseThrow();
	}

	private List<Map<String, Object>> agenda(String quem) {
		var resposta = api.get("/api/avisos/agenda", quem);
		assertThat(resposta.status()).isEqualTo(200);
		return resposta.lista();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> destino(Map<String, Object> aviso) {
		return (Map<String, Object>) aviso.get("destino");
	}

	/** Os avisos que abrem esta conta, cartão ou pessoa. */
	private static List<Map<String, Object>> da(List<Map<String, Object>> agenda, String id) {
		return agenda.stream().filter(a -> id.equals(destino(a).get("id"))).toList();
	}

	private static List<Map<String, Object>> doTipo(List<Map<String, Object>> agenda, String tipo) {
		return agenda.stream().filter(a -> tipo.equals(a.get("tipo"))).toList();
	}

	private String cartao(String ultimos4) {
		return api.post("/api/cartoes", Map.of("banco", "INTER", "ultimos4", ultimos4, "diaFechamento", 20,
				"diaVencimento", 28), dono).texto("id");
	}

	private void compra(String cartaoId, YearMonth fatura, double valor, String pessoaId) {
		var corpo = Map.<String, Object>of("data", fatura.minusMonths(1).atDay(5).toString(), "descricao", "Compra",
				"valor", valor);
		var id = api.post("/api/cartoes/" + cartaoId + "/faturas/" + fatura + "/lancamentos", corpo, dono).texto("id");
		var responsavel = new HashMap<String, Object>(Map.of("pessoaId", pessoaId, "proximas", false));
		assertThat(api.put("/api/lancamentos/" + id + "/responsavel", responsavel, dono).status()).isEqualTo(200);
	}

	private String pessoa(String nome) {
		return api.post("/api/pessoas", Map.of("nome", nome), dono).texto("id");
	}

	private void pagar(String pessoaId, double valor) {
		assertThat(api.post("/api/receber/pessoas/" + pessoaId + "/pagamentos", Map.of("valor", valor), dono).status())
			.isEqualTo(201);
	}

	private static LocalDateTime nove(LocalDate dia) {
		return dia.atTime(9, 0);
	}

	@Test
	void avisaDoisDiasAntesENoDiaAsNove() {
		var luz = contaFixa("Luz", 130, "PIX_BOLETO", null);
		var proxima = ocorrencia(luz, ATUAL.plusMonths(1));

		var agenda = agenda(dono);

		var vencimento = ATUAL.plusMonths(1).atEndOfMonth();
		assertThat(da(agenda, proxima)).first()
			.satisfies(a -> assertThat(a).containsEntry("tipo", "CONTA_ANTES")
				.containsEntry("quando", nove(vencimento.minusDays(2)).toString() + ":00")
				.containsEntry("titulo", "Luz vence em 2 dias")
				.containsEntry("texto", "R$ 130,00 · toque para marcar como paga.")
				.containsEntry("chave", "conta-antes:" + proxima)
				.containsEntry("destino", Map.of("tela", "CONTA", "id", proxima, "mes", ATUAL.plusMonths(1).toString())));
		// Só o que ainda vai acontecer, nos próximos 60 dias, do mais próximo para o mais distante.
		var agora = LocalDateTime.now(FUSO);
		var quandos = agenda.stream().map(a -> LocalDateTime.parse((String) a.get("quando"))).toList();
		assertThat(quandos).isSorted().allMatch(q -> q.isAfter(agora))
			.allMatch(q -> !q.toLocalDate().isAfter(agora.toLocalDate().plusDays(60)));
		// A deste mês tem o aviso do dia enquanto as 9h do vencimento não passaram.
		var desteMes = da(agenda, ocorrencia(luz, ATUAL));
		var noDia = nove(ATUAL.atEndOfMonth());
		assertThat(desteMes).extracting(a -> a.get("tipo")).contains(noDia.isAfter(agora) ? "CONTA_NO_DIA" : "CONTA_ANTES");
	}

	@Test
	void pagaNoCartaoOuEmDebitoNaoTemAvisoDoDia() {
		var luz = contaFixa("Luz", 130, "PIX_BOLETO", null);
		var agua = contaFixa("Água", 80, "DEBITO_AUTOMATICO", null);
		var cartao = api.post("/api/cartoes", Map.of("banco", "INTER", "ultimos4", "9453", "diaFechamento", 28,
				"diaVencimento", 8), dono).texto("id");
		var streaming = contaFixa("Streaming", 39.9, "CARTAO", cartao);
		var luzDoMes = ocorrencia(luz, ATUAL);
		assertThat(api.post("/api/contas-fixas/ocorrencias/" + luzDoMes + "/pagamento", Map.of(), dono).status())
			.isEqualTo(200);

		var agenda = agenda(dono);

		assertThat(da(agenda, luzDoMes)).isEmpty();
		assertThat(da(agenda, ocorrencia(streaming, ATUAL.plusMonths(1)))).isEmpty();
		var aguaProxima = da(agenda, ocorrencia(agua, ATUAL.plusMonths(1)));
		assertThat(aguaProxima).extracting(a -> a.get("tipo")).containsExactly("CONTA_ANTES");
		assertThat(aguaProxima.get(0)).containsEntry("titulo", "Água: débito automático em 2 dias")
			.containsEntry("texto", "Deixe R$ 80,00 na conta para o débito.");
	}

	@Test
	void faturaFechouEQuemAindaDeve() {
		// A fatura do próximo mês fecha no dia 20 e vence no 28: as duas datas ainda vão chegar.
		var mes = ATUAL.plusMonths(1);
		var inter = cartao("9453");
		var nubank = cartao("1234");
		var lucas = pessoa("Lucas");
		var ana = pessoa("Ana");
		compra(inter, mes, 120, lucas);
		compra(inter, mes, 80, ana);
		pagar(ana, 30);

		var agenda = agenda(dono);

		var fechou = doTipo(da(agenda, inter), "FATURA_FECHOU").stream()
			.filter(a -> mes.toString().equals(destino(a).get("mes")))
			.findFirst()
			.orElseThrow();
		assertThat(fechou).containsEntry("titulo", "Fatura Inter •• 9453 fechou")
			.containsEntry("texto", "Importe o arquivo do banco para dividir as compras.")
			.containsEntry("quando", nove(mes.atDay(21)) + ":00")
			.containsEntry("chave", "fatura-fechou:" + inter + ":" + mes);
		assertThat(doTipo(agenda, "FATURA_A_RECEBER")).singleElement()
			.satisfies(a -> assertThat(a).containsEntry("titulo", "Fatura Inter •• 9453 vence em 3 dias")
				.containsEntry("texto", "Faltam R$ 170,00 de Lucas e Ana.")
				.containsEntry("acao", "Cobrar")
				.containsEntry("quando", nove(mes.atDay(25)) + ":00")
				.satisfies(x -> assertThat(destino(x)).containsEntry("tela", "RECEBER").containsEntry("id", null)));
		// Sem ninguém devendo, o Nubank só lembra de importar.
		assertThat(da(agenda, nubank)).extracting(a -> a.get("tipo")).containsOnly("FATURA_FECHOU");

		pagar(lucas, 120);

		assertThat(doTipo(agenda(dono), "FATURA_A_RECEBER")).singleElement()
			.satisfies(a -> assertThat(a).containsEntry("texto", "Faltam R$ 50,00 de Ana.")
				.satisfies(x -> assertThat(destino(x)).containsEntry("tela", "PESSOA").containsEntry("id", ana)));

		pagar(ana, 50);

		assertThat(doTipo(agenda(dono), "FATURA_A_RECEBER")).isEmpty();
	}

	@Test
	void noMaximoSessentaOsMaisProximos() {
		for (int i = 1; i <= 35; i++) {
			contaFixa("Conta " + i, 10, "PIX_BOLETO", null);
		}

		var agenda = agenda(dono);

		assertThat(agenda).hasSize(60);
		assertThat(agenda.stream().map(a -> (String) a.get("quando")).toList()).isSorted();
		assertThat(agenda(api.novoDono())).isEmpty();
	}

}
