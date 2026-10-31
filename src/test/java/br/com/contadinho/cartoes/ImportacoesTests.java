package br.com.contadinho.cartoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.contadinho.ClienteTeste;
import br.com.contadinho.TestcontainersConfiguration;
import br.com.contadinho.cartoes.importacao.LeitorOfx;
import br.com.contadinho.cartoes.importacao.LeitorPdf;
import br.com.contadinho.cartoes.service.ImportacaoServico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * HU-06, HU-07, RN-09, RN-11, RN-12 e RN-59 contra um Postgres real. O cartão fecha dia 28 e vence dia 8; a
 * última compra do arquivo é de 21/10, então a fatura sugerida é a de novembro de 2026.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ImportacoesTests {

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String cartaoId;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		cartaoId = api.post("/api/cartoes", Map.of("banco", "NUBANK", "ultimos4", "3310", "diaFechamento", 28,
				"diaVencimento", 8), dono).texto("id");
	}

	private static Map<String, Object> arquivo(byte[] conteudo, Object... pares) {
		var corpo = new HashMap<String, Object>(Map.of("nomeArquivo", "fatura.ofx", "conteudo",
				Base64.getEncoder().encodeToString(conteudo)));
		for (int i = 0; i < pares.length; i += 2) {
			corpo.put((String) pares[i], pares[i + 1]);
		}
		return corpo;
	}

	private ClienteTeste.Resposta previa(Map<String, Object> corpo) {
		return api.post("/api/cartoes/" + cartaoId + "/importacoes/previa", corpo, dono);
	}

	private ClienteTeste.Resposta importar(List<Map<String, Object>> mesmas) {
		return api.post("/api/cartoes/" + cartaoId + "/importacoes",
				arquivo(ArquivosOfx.sgml(), "mes", "2026-11", "mesmas", mesmas), dono);
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> itens(ClienteTeste.Resposta resposta) {
		return (List<Map<String, Object>>) resposta.objeto().get("itens");
	}

	private String digitar(String data, double valor) {
		return api.post("/api/cartoes/" + cartaoId + "/faturas/2026-11/lancamentos",
				Map.of("data", data, "descricao", "Padaria", "valor", valor), dono).texto("id");
	}

	@Test
	void previaSugereAFaturaEMostraOsTotais() {
		var previa = previa(arquivo(ArquivosOfx.sgml()));

		assertThat(previa.status()).isEqualTo(200);
		assertThat(previa.objeto()).containsEntry("mes", "2026-11").containsEntry("fechamento", "2026-10-28")
			.containsEntry("vencimento", "2026-11-08").containsEntry("novos", 4).containsEntry("jaExistiam", 0)
			.containsEntry("ignorados", 1).containsEntry("totalLido", 445.9).containsEntry("totalArquivo", 445.9);
		assertThat(itens(previa)).extracting(i -> i.get("descricao")).contains("Padaria São João");
		assertThat(jdbc.queryForObject("select count(*) from lancamento where cartao_id = ?::uuid", Integer.class, cartaoId)).isZero();
	}

	@Test
	void previaAceitaOutraFatura() {
		var previa = previa(arquivo(ArquivosOfx.sgml(), "mes", "2026-12"));

		assertThat(previa.objeto()).containsEntry("mes", "2026-12").containsEntry("fechamento", "2026-11-28");
	}

	@Test
	void importaEReimportarNaoDuplica() {
		var primeira = importar(List.of());
		var segunda = importar(List.of());

		assertThat(primeira.status()).isEqualTo(201);
		assertThat(primeira.objeto()).containsEntry("mes", "2026-11").containsEntry("novos", 4)
			.containsEntry("jaExistiam", 0).containsEntry("casadas", 0);
		assertThat(segunda.objeto()).containsEntry("novos", 0).containsEntry("jaExistiam", 4);
		var fatura = api.get("/api/cartoes/" + cartaoId + "/faturas/2026-11", dono).objeto();
		assertThat(fatura.get("total")).isEqualTo(445.9);
		assertThat((List<?>) fatura.get("lancamentos")).hasSize(4)
			.allSatisfy(l -> assertThat(((Map<?, ?>) l).get("origem")).isEqualTo("ARQUIVO"));
		assertThat(jdbc.queryForObject("select parcela_atual from lancamento where id_externo = 'abc-002'", Integer.class)).isEqualTo(2);
	}

	@Test
	void compraDigitadaParecidaEMostradaEPodeSerAMesma() {
		var digitada = digitar("2026-10-03", 120.50);
		var outra = digitar("2026-10-03", 99);
		var pessoa = UUID.randomUUID();
		jdbc.update("insert into pessoa (id, usuario_id, nome) select ?, usuario_id, 'Mãe' from lancamento where id = ?::uuid",
				pessoa, digitada);
		jdbc.update("update lancamento set pessoa_id = ? where id = ?::uuid", pessoa, digitada);

		var previa = previa(arquivo(ArquivosOfx.sgml()));
		var padaria = itens(previa).getFirst();
		assertThat(padaria.get("parecida")).isEqualTo(Map.of("id", digitada, "descricao", "Padaria"));
		assertThat(itens(previa)).filteredOn(i -> i.get("parecida") != null).hasSize(1);

		var resultado = importar(List.of(Map.of("idExterno", "abc-001", "lancamentoId", digitada)));

		assertThat(resultado.objeto()).containsEntry("novos", 3).containsEntry("casadas", 1);
		var casada = jdbc.queryForMap("select origem, descricao, id_externo, pessoa_id from lancamento where id = ?::uuid", digitada);
		assertThat(casada).containsEntry("origem", "ARQUIVO").containsEntry("descricao", "Padaria São João")
			.containsEntry("id_externo", "abc-001").containsEntry("pessoa_id", pessoa);
		assertThat(jdbc.queryForObject("select origem from lancamento where id = ?::uuid", String.class, outra)).isEqualTo("MANUAL");
		assertThat(api.get("/api/cartoes/" + cartaoId + "/faturas/2026-11", dono).objeto().get("total")).isEqualTo(544.9);
	}

	@Test
	void quandoEOutraCompraAsDuasFicam() {
		digitar("2026-10-03", 120.50);

		var resultado = importar(List.of());

		assertThat(resultado.objeto()).containsEntry("novos", 4).containsEntry("casadas", 0);
		assertThat(api.get("/api/cartoes/" + cartaoId + "/faturas/2026-11", dono).objeto().get("total")).isEqualTo(566.4);
	}

	@Test
	void soCasaComAParecidaDaPrevia() {
		var semPar = digitar("2026-10-04", 7);

		var resultado = importar(List.of(Map.of("idExterno", "abc-001", "lancamentoId", semPar)));

		assertThat(resultado.status()).isEqualTo(409);
		assertThat(resultado.texto("detail")).isEqualTo(ImportacaoServico.FATURA_MUDOU);
		assertThat(jdbc.queryForObject("select count(*) from importacao i join fatura f on f.id = i.fatura_id where f.cartao_id = ?::uuid",
				Integer.class, cartaoId)).isZero();
	}

	@Test
	void arquivoQueNaoEOfxERecusado() {
		var csv = api.post("/api/cartoes/" + cartaoId + "/importacoes/previa",
				Map.of("nomeArquivo", "fatura.csv", "conteudo", Base64.getEncoder().encodeToString("a;b;c".getBytes())), dono);
		var quebrado = previa(Map.of("nomeArquivo", "fatura.ofx", "conteudo", "isto não é base64"));

		assertThat(csv.status()).isEqualTo(422);
		assertThat(csv.texto("detail")).isEqualTo(LeitorOfx.E_CSV);
		assertThat(quebrado.status()).isEqualTo(400);
	}

	@Test
	void pdfComSenhaPedeASenhaENaoGuardaNada() {
		var pdf = Base64.getEncoder().encodeToString(ArquivosPdf.inter("12345"));

		var semSenha = previa(Map.of("nomeArquivo", "Fatura.pdf", "conteudo", pdf));
		var senhaErrada = previa(Map.of("nomeArquivo", "Fatura.pdf", "conteudo", pdf, "senha", "00000"));

		assertThat(semSenha.status()).isEqualTo(422);
		assertThat(semSenha.objeto()).containsEntry("codigo", "SENHA_PDF").containsEntry("detail", LeitorPdf.PEDE_SENHA);
		assertThat(senhaErrada.objeto()).containsEntry("codigo", "SENHA_PDF_INCORRETA");
		assertThat(jdbc.queryForObject("select count(*) from importacao i join fatura f on f.id = i.fatura_id where f.cartao_id = ?::uuid",
				Integer.class, cartaoId)).isZero();
	}

	@Test
	void importaOPdfDoInterNaFaturaQueOArquivoInforma() {
		var pdf = Base64.getEncoder().encodeToString(ArquivosPdf.inter("12345"));
		var corpo = Map.<String, Object>of("nomeArquivo", "Fatura.pdf", "conteudo", pdf, "senha", "12345");

		var previa = previa(corpo);
		assertThat(previa.status()).isEqualTo(200);
		// O vencimento escrito no PDF (12/11) manda; pelas datas, a sugestão seria outra.
		assertThat(previa.objeto()).containsEntry("mes", "2026-11").containsEntry("novos", 4).containsEntry("ignorados", 1)
			.containsEntry("totalLido", 467.4).containsEntry("totalArquivo", 467.4);
		assertThat(itens(previa)).extracting(i -> i.get("finalCartao")).containsExactly("1111", "1111", "2222", "2222");

		var confirmacao = new HashMap<String, Object>(corpo);
		confirmacao.putAll(Map.of("mes", "2026-11", "mesmas", List.of()));
		var importada = api.post("/api/cartoes/" + cartaoId + "/importacoes", confirmacao, dono);
		var deNovo = api.post("/api/cartoes/" + cartaoId + "/importacoes", confirmacao, dono);

		assertThat(importada.status()).isEqualTo(201);
		assertThat(importada.objeto()).containsEntry("novos", 4);
		assertThat(deNovo.objeto()).containsEntry("novos", 0).containsEntry("jaExistiam", 4);
		assertThat(jdbc.queryForList("select formato, modelo from importacao where id = ?::uuid", importada.texto("id")))
			.containsExactly(Map.of("formato", "PDF", "modelo", "PDF_INTER"));
		var fatura = api.get("/api/cartoes/" + cartaoId + "/faturas/2026-11", dono).objeto();
		assertThat(fatura.get("total")).isEqualTo(467.4);
		List<Object> finais = ((List<?>) fatura.get("lancamentos")).stream()
			.<Object>map(l -> ((Map<?, ?>) l).get("finalCartao"))
			.toList();
		assertThat(finais).containsOnly("1111", "2222");
	}

	@Test
	void cartaoDeOutroDonoNaoImporta() {
		var outroDono = api.novoDono();

		var resposta = api.post("/api/cartoes/" + cartaoId + "/importacoes/previa", arquivo(ArquivosOfx.sgml()), outroDono);

		assertThat(resposta.status()).isEqualTo(404);
	}

}
