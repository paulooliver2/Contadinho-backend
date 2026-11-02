package br.com.contadinho.cartoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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
 * HU-44: desfazer uma importação. A ligação com contas fixas está em ContaNoCartaoTests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DesfazerImportacaoTests {

	private static final YearMonth FATURA = YearMonth.now(ZoneId.of("America/Sao_Paulo")).plusMonths(1);

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String cartao;

	private String lucas;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		cartao = api.post("/api/cartoes", Map.of("apelido", "Inter", "banco", "INTER", "ultimos4", "9453",
				"diaFechamento", 20, "diaVencimento", 28), dono).texto("id");
		lucas = api.post("/api/pessoas", Map.of("nome", "Lucas"), dono).texto("id");
	}

	/** Compras como {id externo, valor, descrição}, todas do dia 5 do mês anterior ao vencimento. */
	private String importar(String arquivo, List<Map<String, String>> mesmas, String[]... compras) {
		var ofx = new StringBuilder("""
				OFXHEADER:100
				DATA:OFXSGML
				VERSION:102
				ENCODING:USASCII
				CHARSET:1252

				<OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><CURDEF>BRL<BANKTRANLIST>
				""");
		var data = FATURA.minusMonths(1).atDay(5).format(DateTimeFormatter.BASIC_ISO_DATE);
		for (var c : compras) {
			ofx.append("<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>").append(data).append("<TRNAMT>-").append(c[1])
				.append("<FITID>").append(c[0]).append("<MEMO>").append(c[2]).append("</STMTTRN>\n");
		}
		ofx.append("</BANKTRANLIST></CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1></OFX>\n");
		var resposta = api.post("/api/cartoes/" + cartao + "/importacoes", Map.of("nomeArquivo", arquivo, "conteudo",
				Base64.getEncoder().encodeToString(ofx.toString().getBytes(StandardCharsets.US_ASCII)), "mes",
				FATURA.toString(), "mesmas", mesmas), dono);
		assertThat(resposta.status()).isEqualTo(201);
		return resposta.texto("id");
	}

	private Map<String, Object> fatura() {
		return api.get("/api/cartoes/" + cartao + "/faturas/" + FATURA, dono).objeto();
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> lancamentos() {
		return (List<Map<String, Object>>) fatura().get("lancamentos");
	}

	private String idDe(String descricao) {
		return lancamentos().stream().filter(l -> l.get("descricao").equals(descricao)).map(l -> (String) l.get("id"))
			.findFirst().orElseThrow();
	}

	private void doLucas(String descricao) {
		assertThat(api.put("/api/lancamentos/" + idDe(descricao) + "/responsavel",
				Map.of("pessoaId", lucas, "proximas", false), dono).status()).isEqualTo(200);
	}

	@SuppressWarnings("unchecked")
	private Object saldoDoLucas() {
		return ((Map<String, Object>) api.get("/api/receber/pessoas/" + lucas, dono).objeto().get("situacao")).get("saldo");
	}

	@Test
	@SuppressWarnings("unchecked")
	void aFaturaListaAsImportacoes() {
		var id = importar("fatura.ofx", List.of(), new String[] { "t1", "120.00", "TENIS" },
				new String[] { "m1", "80.00", "MERCADO" });

		var importacoes = (List<Map<String, Object>>) fatura().get("importacoes");

		assertThat(importacoes).hasSize(1);
		assertThat(importacoes.getFirst()).containsEntry("id", id).containsEntry("nomeArquivo", "fatura.ofx")
			.containsEntry("formato", "OFX").containsEntry("compras", 2).containsKey("importadaEm");
	}

	@Test
	void desfazerTiraAsComprasEOQueAsPessoasDeviam() {
		var id = importar("fatura.ofx", List.of(), new String[] { "t1", "120.00", "TENIS" },
				new String[] { "m1", "80.00", "MERCADO" });
		doLucas("TENIS");
		assertThat(saldoDoLucas()).isEqualTo(120.0);

		var previa = api.get("/api/importacoes/" + id + "/desfazer", dono).objeto();
		assertThat(previa).containsEntry("removidas", 2).containsEntry("voltamADigitadas", 0)
			.containsEntry("pagos", List.of()).containsEntry("mes", FATURA.toString());

		assertThat(api.delete("/api/importacoes/" + id, dono).status()).isEqualTo(200);

		assertThat(lancamentos()).isEmpty();
		assertThat(fatura().get("importacoes")).isEqualTo(List.of());
		assertThat(saldoDoLucas()).isEqualTo(0);
		// A fatura sem nada deixa de existir: o cartão volta a poder ser excluído (HU-45).
		assertThat(jdbc.queryForObject("select count(*) from fatura where cartao_id = ?::uuid", Integer.class, cartao))
			.isZero();
		// Dá para importar o mesmo arquivo de novo, agora no lugar certo.
		importar("fatura.ofx", List.of(), new String[] { "t1", "120.00", "TENIS" });
		assertThat(lancamentos()).hasSize(1);
	}

	@Test
	void pagamentoQueAbatiaAsComprasViraCredito() {
		var id = importar("fatura.ofx", List.of(), new String[] { "t1", "120.00", "TENIS" });
		doLucas("TENIS");
		api.post("/api/receber/pessoas/" + lucas + "/pagamentos", Map.of("valor", 50), dono);

		var previa = api.get("/api/importacoes/" + id + "/desfazer", dono).objeto();
		assertThat(previa.get("pagos")).isEqualTo(List.of(Map.of("pessoaId", lucas, "valor", 50.0)));

		api.delete("/api/importacoes/" + id, dono);

		assertThat(saldoDoLucas()).isEqualTo(-50.0);
	}

	@Test
	void desfazUmaImportacaoSoEACompraDigitadaCasadaVolta() {
		// Digitada antes, depois casada com a do arquivo (RN-59).
		api.post("/api/cartoes/" + cartao + "/faturas/" + FATURA + "/lancamentos",
				Map.of("data", FATURA.minusMonths(1).atDay(5).toString(), "descricao", "Tênis", "valor", 120), dono);
		var digitada = idDe("Tênis");
		doLucas("Tênis");
		var primeira = importar("parcial.ofx", List.of(), new String[] { "m1", "80.00", "MERCADO" });
		var segunda = importar("completa.ofx", List.of(Map.of("idExterno", "t1", "lancamentoId", digitada)),
				new String[] { "t1", "120.00", "TENIS" }, new String[] { "f1", "30.00", "FARMACIA" });

		var previa = api.get("/api/importacoes/" + segunda + "/desfazer", dono).objeto();
		assertThat(previa).containsEntry("removidas", 1).containsEntry("voltamADigitadas", 1);

		api.delete("/api/importacoes/" + segunda, dono);

		assertThat(lancamentos()).extracting(l -> l.get("descricao")).containsExactlyInAnyOrder("TENIS", "MERCADO");
		var tenis = lancamentos().stream().filter(l -> l.get("id").equals(digitada)).findFirst().orElseThrow();
		assertThat(tenis).containsEntry("origem", "MANUAL").containsEntry("pessoaId", lucas);
		assertThat(saldoDoLucas()).isEqualTo(120.0);
		assertThat(api.get("/api/importacoes/" + primeira + "/desfazer", dono).status()).isEqualTo(200);
	}

	@Test
	void cadaDonoSoDesfazAsProprias() {
		var id = importar("fatura.ofx", List.of(), new String[] { "t1", "120.00", "TENIS" });
		var outro = api.novoDono();

		assertThat(api.get("/api/importacoes/" + id + "/desfazer", outro).status()).isEqualTo(404);
		assertThat(api.delete("/api/importacoes/" + id, outro).status()).isEqualTo(404);
		assertThat(lancamentos()).hasSize(1);
	}

}
