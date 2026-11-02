package br.com.contadinho.cartoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
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
 * HU-09 e RN-18: o responsável sugerido pelas duas últimas atribuições da mesma descrição.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SugestaoResponsavelTests {

	private static final YearMonth FATURA = YearMonth.now(ZoneId.of("America/Sao_Paulo")).plusMonths(1);

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String cartao;

	private String lucas;

	private String ana;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		cartao = api.post("/api/cartoes", Map.of("apelido", "Inter", "banco", "INTER", "ultimos4", "9453",
				"diaFechamento", 20, "diaVencimento", 28), dono).texto("id");
		lucas = api.post("/api/pessoas", Map.of("nome", "Lucas"), dono).texto("id");
		ana = api.post("/api/pessoas", Map.of("nome", "Ana"), dono).texto("id");
	}

	/** Compras como {id externo, dia, descrição}, de R$ 10, no mês anterior ao vencimento. */
	private Map<String, Object> arquivo(String[]... compras) {
		var ofx = new StringBuilder("""
				OFXHEADER:100
				DATA:OFXSGML
				VERSION:102
				ENCODING:USASCII
				CHARSET:1252

				<OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><CURDEF>BRL<BANKTRANLIST>
				""");
		for (var c : compras) {
			var data = FATURA.minusMonths(1).atDay(Integer.parseInt(c[1])).format(DateTimeFormatter.BASIC_ISO_DATE);
			ofx.append("<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>").append(data).append("<TRNAMT>-10.00<FITID>").append(c[0])
				.append("<MEMO>").append(c[2]).append("</STMTTRN>\n");
		}
		ofx.append("</BANKTRANLIST></CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1></OFX>\n");
		var corpo = new HashMap<String, Object>();
		corpo.put("nomeArquivo", "fatura.ofx");
		corpo.put("conteudo", Base64.getEncoder().encodeToString(ofx.toString().getBytes(StandardCharsets.US_ASCII)));
		corpo.put("mes", FATURA.toString());
		corpo.put("mesmas", List.of());
		return corpo;
	}

	private void importar(String[]... compras) {
		assertThat(api.post("/api/cartoes/" + cartao + "/importacoes", arquivo(compras), dono).status()).isEqualTo(201);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> itemDaPrevia(String idExterno, String[]... compras) {
		var previa = api.post("/api/cartoes/" + cartao + "/importacoes/previa", arquivo(compras), dono).objeto();
		return ((List<Map<String, Object>>) previa.get("itens")).stream().filter(i -> i.get("idExterno").equals(idExterno))
			.findFirst().orElseThrow();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> lancamento(String descricao, int dia) {
		var dataIso = FATURA.minusMonths(1).atDay(dia).toString();
		return ((List<Map<String, Object>>) api.get("/api/cartoes/" + cartao + "/faturas/" + FATURA, dono).objeto()
			.get("lancamentos")).stream()
			.filter(l -> l.get("descricao").equals(descricao) && l.get("data").equals(dataIso))
			.findFirst().orElseThrow();
	}

	private void atribuir(String descricao, int dia, String pessoa) {
		var corpo = new HashMap<String, Object>();
		corpo.put("pessoaId", pessoa);
		corpo.put("proximas", false);
		assertThat(api.put("/api/lancamentos/" + lancamento(descricao, dia).get("id") + "/responsavel", corpo, dono)
			.status()).isEqualTo(200);
	}

	@SuppressWarnings("unchecked")
	private Object saldo(String pessoa) {
		return ((Map<String, Object>) api.get("/api/receber/pessoas/" + pessoa, dono).objeto().get("situacao")).get("saldo");
	}

	/** Duas padarias do Lucas, nos dias 3 e 4. */
	private void duasVezesDoLucas() {
		importar(new String[] { "p1", "3", "PADARIA SAO JOAO" }, new String[] { "p2", "4", "PADARIA SAO JOAO" });
		atribuir("PADARIA SAO JOAO", 3, lucas);
		atribuir("PADARIA SAO JOAO", 4, lucas);
	}

	@Test
	void duasVezesComAMesmaPessoaViraSugestaoNaPreviaENaFatura() {
		duasVezesDoLucas();
		assertThat(saldo(lucas)).isEqualTo(20.0);
		// Maiúsculas e símbolos não importam: a descrição normalizada é a mesma.
		var nova = new String[] { "p3", "10", "Padaria Sao-Joao*" };
		var outra = new String[] { "m1", "10", "MERCADO" };

		assertThat(itemDaPrevia("p3", nova, outra)).containsEntry("pessoaSugeridaId", lucas);
		assertThat(itemDaPrevia("m1", nova, outra).get("pessoaSugeridaId")).isNull();

		importar(nova, outra);

		// HU-09.2: confirmada, a sugestão vira atribuição e o Lucas passa a dever a compra.
		assertThat(lancamento("Padaria Sao-Joao*", 10)).containsEntry("pessoaId", lucas).containsEntry("sugerido", true);
		assertThat(lancamento("MERCADO", 10)).containsEntry("sugerido", false);
		assertThat(lancamento("PADARIA SAO JOAO", 3)).containsEntry("sugerido", false);
		assertThat(saldo(lucas)).isEqualTo(30.0);
	}

	@Test
	void oSeloSaiQuandoODonoTocaNoResponsavel() {
		duasVezesDoLucas();
		importar(new String[] { "p3", "10", "PADARIA SAO JOAO" });

		atribuir("PADARIA SAO JOAO", 10, lucas);

		assertThat(lancamento("PADARIA SAO JOAO", 10)).containsEntry("pessoaId", lucas).containsEntry("sugerido", false);
	}

	@Test
	void naoSugereSemDuasVezesIguaisDeUmaPessoa() {
		importar(new String[] { "a1", "3", "FARMACIA" }, new String[] { "a2", "4", "FARMACIA" },
				new String[] { "b1", "3", "POSTO" }, new String[] { "c1", "3", "CINEMA" }, new String[] { "c2", "4", "CINEMA" });
		// Pessoas diferentes nas duas últimas vezes.
		atribuir("FARMACIA", 3, lucas);
		atribuir("FARMACIA", 4, ana);
		// Só uma vez.
		atribuir("POSTO", 3, lucas);
		// A mais antiga era do Lucas, mas a última ficou com o dono.
		atribuir("CINEMA", 3, lucas);

		var compras = new String[][] { { "a3", "10", "FARMACIA" }, { "b2", "10", "POSTO" }, { "c3", "10", "CINEMA" } };
		for (var id : List.of("a3", "b2", "c3")) {
			assertThat(itemDaPrevia(id, compras).get("pessoaSugeridaId")).as(id).isNull();
		}
	}

	@Test
	void pessoaArquivadaNaoESugerida() {
		duasVezesDoLucas();
		// Só arquiva com o saldo zerado.
		api.post("/api/receber/pessoas/" + lucas + "/pagamentos", Map.of("valor", 20), dono);
		assertThat(api.post("/api/pessoas/" + lucas + "/arquivar", Map.of(), dono).status()).isLessThan(300);

		assertThat(itemDaPrevia("p3", new String[] { "p3", "10", "PADARIA SAO JOAO" }).get("pessoaSugeridaId")).isNull();
	}

	@Test
	void naoUsaAsAtribuicoesDeOutroDono() {
		duasVezesDoLucas();
		var outro = api.novoDono();
		var cartaoDoOutro = api.post("/api/cartoes", Map.of("apelido", "Inter", "banco", "INTER", "ultimos4", "1111",
				"diaFechamento", 20, "diaVencimento", 28), outro).texto("id");

		var previa = api.post("/api/cartoes/" + cartaoDoOutro + "/importacoes/previa",
				arquivo(new String[] { "p3", "10", "PADARIA SAO JOAO" }), outro).objeto();

		assertThat(previa.get("itens")).asList().singleElement()
			.satisfies(i -> assertThat(((Map<?, ?>) i).get("pessoaSugeridaId")).isNull());
	}

}
