package br.com.contadinho.gastos;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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
 * HU-14 e RN-23 contra um Postgres real. As contas vencem dia 31, ou seja, no último dia do mês atual; as compras
 * do arquivo ficam em volta desse dia.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContaNoCartaoTests {

	private static final YearMonth ATUAL = YearMonth.now(ZoneId.of("America/Sao_Paulo"));

	private static final LocalDate VENCIMENTO = ATUAL.atEndOfMonth();

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String cartao;

	private String contas;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		cartao = novoCartao("3310");
		contas = api.get("/api/categorias", dono).lista().stream()
			.filter(c -> c.get("nome").equals("Assinaturas"))
			.map(c -> (String) c.get("id"))
			.findFirst()
			.orElseThrow();
	}

	private String novoCartao(String final4) {
		return api.post("/api/cartoes", Map.of("banco", "NUBANK", "ultimos4", final4, "diaFechamento", 28,
				"diaVencimento", 8), dono).texto("id");
	}

	private void conta(String descricao, double valor, String forma, String cartaoId) {
		var corpo = new HashMap<String, Object>(Map.of("descricao", descricao, "valor", valor, "categoriaId", contas,
				"diaVencimento", 31, "formaPagamento", forma));
		corpo.put("cartaoId", cartaoId);
		assertThat(api.post("/api/contas-fixas", corpo, dono).status()).isEqualTo(201);
	}

	/** Compras como {id, dias a partir do vencimento, valor, descrição}. */
	private void importar(Object[]... compras) {
		var ofx = new StringBuilder("""
				OFXHEADER:100
				DATA:OFXSGML
				VERSION:102
				ENCODING:USASCII
				CHARSET:1252

				<OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><CURDEF>BRL<BANKTRANLIST>
				""");
		for (var c : compras) {
			ofx.append("<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>")
				.append(VENCIMENTO.plusDays((int) c[1]).format(DateTimeFormatter.BASIC_ISO_DATE))
				.append("<TRNAMT>-").append(c[2]).append("<FITID>").append(c[0]).append("<MEMO>").append(c[3])
				.append("</STMTTRN>\n");
		}
		ofx.append("</BANKTRANLIST></CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1></OFX>\n");
		var corpo = Map.<String, Object>of("nomeArquivo", "fatura.ofx", "conteudo",
				Base64.getEncoder().encodeToString(ofx.toString().getBytes(StandardCharsets.US_ASCII)), "mes",
				ATUAL.plusMonths(1).toString(), "mesmas", List.of());
		assertThat(api.post("/api/cartoes/" + cartao + "/importacoes", corpo, dono).status()).isEqualTo(201);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> mes() {
		return api.get("/api/contas-fixas/mes/" + ATUAL, dono).objeto();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> doMes(String descricao) {
		return ((List<Map<String, Object>>) mes().get("contas")).stream()
			.filter(c -> ((Map<String, Object>) c.get("conta")).get("descricao").equals(descricao))
			.findFirst()
			.orElseThrow();
	}

	private String id(String descricao) {
		return (String) doMes(descricao).get("id");
	}

	private String lancamento(String idExterno) {
		return jdbc.queryForObject("select id::text from lancamento where id_externo = ? and cartao_id = ?::uuid",
				String.class, idExterno, cartao);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> compra(Map<String, Object> conta) {
		return (Map<String, Object>) conta.get("compra");
	}

	@Test
	@SuppressWarnings("unchecked")
	void importarLigaAContaAoLancamentoParecido() {
		conta("Netflix", 55.90, "CARTAO", cartao);
		conta("Spotify", 21.90, "CARTAO", cartao);
		conta("Celular", 60, "CARTAO", cartao);

		// O mês ainda não foi aberto: as ocorrências nascem na importação.
		importar(new Object[] { "n1", -2, "56.50", "NETFLIX.COM" },
				new Object[] { "s1", -9, "21.90", "SPOTIFY" },
				new Object[] { "c1", 1, "61.50", "OPERADORA CEL" });

		var netflix = doMes("Netflix");
		assertThat(netflix).containsEntry("status", "NA_FATURA").containsEntry("valor", 56.5)
			.containsEntry("valorPrevisto", 55.9);
		assertThat(compra(netflix)).containsEntry("descricao", "NETFLIX.COM")
			.containsEntry("fatura", ATUAL.plusMonths(1).toString());
		assertThat((String) compra(netflix).get("cartao")).endsWith("•• 3310");
		// Longe demais do vencimento, ou mais de R$ 1 de diferença: continua aguardando.
		assertThat(doMes("Spotify")).containsEntry("status", "AGUARDANDO_FATURA");
		assertThat(doMes("Celular")).containsEntry("status", "AGUARDANDO_FATURA");
		// No total, vale o lançamento: 56,50 + 21,90 + 60,00.
		assertThat(mes()).containsEntry("noCartao", 138.4);
	}

	@Test
	void umaCompraPagaUmaContaSo() {
		conta("Academia", 99.90, "CARTAO", cartao);
		conta("Academia da filha", 99.90, "CARTAO", cartao);

		importar(new Object[] { "a1", 0, "99.90", "SMARTFIT" });

		var ligadas = List.of(doMes("Academia"), doMes("Academia da filha")).stream()
			.filter(c -> c.get("status").equals("NA_FATURA"))
			.count();
		assertThat(ligadas).isEqualTo(1);
		var aguardando = doMes("Academia").get("status").equals("NA_FATURA") ? "Academia da filha" : "Academia";
		var ligar = api.put("/api/contas-fixas/ocorrencias/" + id(aguardando) + "/lancamento",
				Map.of("lancamentoId", lancamento("a1")), dono);
		assertThat(ligar.status()).isEqualTo(409);
		assertThat(ligar.texto("detail")).isEqualTo("Essa compra já paga outra conta fixa.");
	}

	@Test
	void ligarEDesligarAMao() {
		conta("Celular", 60, "CARTAO", cartao);
		importar(new Object[] { "c1", 1, "61.50", "OPERADORA CEL" }, new Object[] { "p1", -3, "12.00", "PADARIA" });
		var id = id("Celular");

		var candidatas = api.get("/api/contas-fixas/ocorrencias/" + id + "/candidatas", dono).lista();

		assertThat(candidatas).extracting(c -> c.get("descricao")).containsExactly("OPERADORA CEL", "PADARIA");
		var ligada = api.put("/api/contas-fixas/ocorrencias/" + id + "/lancamento",
				Map.of("lancamentoId", candidatas.get(0).get("id")), dono);
		assertThat(ligada.status()).isEqualTo(200);
		assertThat(ligada.objeto()).containsEntry("status", "NA_FATURA").containsEntry("valor", 61.5);

		var desligada = api.delete("/api/contas-fixas/ocorrencias/" + id + "/lancamento", dono);

		assertThat(desligada.objeto()).containsEntry("status", "AGUARDANDO_FATURA").containsEntry("valor", 60.0);
		assertThat(doMes("Celular")).containsEntry("status", "AGUARDANDO_FATURA");
	}

	@Test
	void desligadaNaoVoltaAoReimportar() {
		conta("Netflix", 55.90, "CARTAO", cartao);
		importar(new Object[] { "n1", 0, "55.90", "NETFLIX.COM" });
		api.delete("/api/contas-fixas/ocorrencias/" + id("Netflix") + "/lancamento", dono);

		importar(new Object[] { "n1", 0, "55.90", "NETFLIX.COM" });

		assertThat(doMes("Netflix")).containsEntry("status", "AGUARDANDO_FATURA");
	}

	@Test
	@SuppressWarnings("unchecked")
	void soContaNoCartaoECompraDoMesmoCartao() {
		conta("Luz", 130, "PIX_BOLETO", null);
		var outro = novoCartao("9453");
		conta("Internet", 120, "CARTAO", outro);
		importar(new Object[] { "i1", 0, "120.00", "PROVEDOR" });

		assertThat(api.get("/api/contas-fixas/ocorrencias/" + id("Luz") + "/candidatas", dono).status()).isEqualTo(409);
		// A compra é do cartão 3310; a conta é paga no 9453.
		assertThat(doMes("Internet")).containsEntry("status", "AGUARDANDO_FATURA");
		var ligar = api.put("/api/contas-fixas/ocorrencias/" + id("Internet") + "/lancamento",
				Map.of("lancamentoId", lancamento("i1")), dono);
		assertThat(ligar.status()).isEqualTo(400);
		assertThat((Map<String, Object>) ligar.objeto().get("campos")).containsEntry("lancamentoId",
				"Escolha uma compra do cartão desta conta.");
	}

	@Test
	void soODonoLiga() {
		conta("Netflix", 55.90, "CARTAO", cartao);
		importar(new Object[] { "n1", 10, "99.00", "OUTRA COISA" });
		var intruso = api.novoDono();

		assertThat(api.get("/api/contas-fixas/ocorrencias/" + id("Netflix") + "/candidatas", intruso).status())
			.isEqualTo(404);
		assertThat(api.put("/api/contas-fixas/ocorrencias/" + id("Netflix") + "/lancamento",
				Map.of("lancamentoId", lancamento("n1")), intruso).status()).isEqualTo(404);
	}

}
