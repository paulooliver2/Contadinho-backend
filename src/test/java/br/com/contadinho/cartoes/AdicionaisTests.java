package br.com.contadinho.cartoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.charset.StandardCharsets;
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
 * HU-38 e RN-19: as compras do cartão adicional chegam atribuídas à pessoa dele. O PDF do Inter dos testes tem o
 * titular (final 1111) e um adicional (final 2222), na fatura de novembro de 2026.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdicionaisTests {

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String cartao;

	private String ana;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		cartao = api.post("/api/cartoes", Map.of("banco", "INTER", "ultimos4", "1111", "diaFechamento", 28,
				"diaVencimento", 8), dono).texto("id");
		ana = api.post("/api/pessoas", Map.of("nome", "Ana"), dono).texto("id");
	}

	private String adicionais() {
		return "/api/cartoes/" + cartao + "/adicionais";
	}

	private ClienteTeste.Resposta cadastrar(String ultimos4, String pessoa) {
		return api.post(adicionais(), Map.of("ultimos4", ultimos4, "pessoaId", pessoa), dono);
	}

	private Map<String, Object> pdf() {
		return Map.of("nomeArquivo", "Fatura.pdf", "conteudo", Base64.getEncoder().encodeToString(ArquivosPdf.inter("12345")),
				"senha", "12345", "mes", "2026-11", "mesmas", List.of());
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> lancamentos() {
		return (List<Map<String, Object>>) api.get("/api/cartoes/" + cartao + "/faturas/2026-11", dono).objeto()
			.get("lancamentos");
	}

	@SuppressWarnings("unchecked")
	private Object saldoDaAna() {
		return ((Map<String, Object>) api.get("/api/receber/pessoas/" + ana, dono).objeto().get("situacao")).get("saldo");
	}

	@Test
	@SuppressWarnings("unchecked")
	void cadastraListaERemove() {
		var criado = cadastrar("2222", ana);
		assertThat(criado.status()).isEqualTo(201);
		assertThat(criado.objeto()).containsEntry("ultimos4", "2222").containsEntry("pessoaId", ana);

		var lista = api.get(adicionais(), dono).objeto();
		assertThat(lista).containsEntry("trazFinal", "SEM_IMPORTACAO");
		assertThat((List<Map<String, Object>>) lista.get("adicionais")).singleElement()
			.satisfies(a -> assertThat(a).containsEntry("ultimos4", "2222"));

		assertThat(api.delete(adicionais() + "/" + criado.texto("id"), dono).status()).isEqualTo(204);
		assertThat(api.get(adicionais(), dono).objeto().get("adicionais")).isEqualTo(List.of());
	}

	@Test
	@SuppressWarnings("unchecked")
	void recusaFinalErradoRepetidoEPessoaForaDaLista() {
		cadastrar("2222", ana);
		var outraPessoa = api.post("/api/pessoas", Map.of("nome", "Bia"), api.novoDono()).texto("id");

		var titular = cadastrar("1111", ana);
		var repetido = cadastrar("2222", ana);
		var curto = cadastrar("22", ana);
		var deOutroDono = cadastrar("3333", outraPessoa);

		assertThat(titular.status()).isEqualTo(400);
		assertThat((Map<String, Object>) titular.objeto().get("campos")).containsKey("ultimos4");
		assertThat((Map<String, Object>) repetido.objeto().get("campos")).containsKey("ultimos4");
		assertThat(curto.status()).isEqualTo(400);
		assertThat((Map<String, Object>) deOutroDono.objeto().get("campos")).containsKey("pessoaId");
	}

	@Test
	void cadaDonoSoVeOsProprios() {
		var id = cadastrar("2222", ana).texto("id");
		var outro = api.novoDono();

		assertThat(api.get(adicionais(), outro).status()).isEqualTo(404);
		assertThat(api.delete(adicionais() + "/" + id, outro).status()).isEqualTo(404);
	}

	@Test
	@SuppressWarnings("unchecked")
	void asComprasDoAdicionalChegamComAPessoa() {
		cadastrar("2222", ana);

		var previa = api.post("/api/cartoes/" + cartao + "/importacoes/previa", pdf(), dono).objeto();
		assertThat((List<Map<String, Object>>) previa.get("itens"))
			.extracting(i -> i.get("finalCartao"), i -> i.get("pessoaAdicionalId"))
			.containsExactly(tuple("1111", null), tuple("1111", null),
					tuple("2222", ana), tuple("2222", ana));

		assertThat(api.post("/api/cartoes/" + cartao + "/importacoes", pdf(), dono).status()).isEqualTo(201);

		// HU-38.2: atribuídas de verdade (não sugeridas), e a Ana deve a parcela menos o estorno: 119,98 - 85,70.
		assertThat(lancamentos()).filteredOn(l -> "2222".equals(l.get("finalCartao")))
			.hasSize(2)
			.allSatisfy(l -> assertThat(l).containsEntry("pessoaId", ana).containsEntry("sugerido", false));
		assertThat(lancamentos()).filteredOn(l -> "1111".equals(l.get("finalCartao")))
			.allSatisfy(l -> assertThat(l.get("pessoaId")).isNull());
		assertThat(saldoDaAna()).isEqualTo(34.28);
		assertThat(api.get(adicionais(), dono).objeto()).containsEntry("trazFinal", "SIM");
	}

	@Test
	void arquivoSemFinalAvisaQueARegraNaoSeAplica() {
		var ofx = """
				OFXHEADER:100
				DATA:OFXSGML

				<OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><CURDEF>BRL<BANKTRANLIST>
				<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261005<TRNAMT>-10.00<FITID>x1<MEMO>MERCADO</STMTTRN>
				</BANKTRANLIST></CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1></OFX>
				""";
		api.post("/api/cartoes/" + cartao + "/importacoes", Map.of("nomeArquivo", "fatura.ofx", "conteudo",
				Base64.getEncoder().encodeToString(ofx.getBytes(StandardCharsets.US_ASCII)), "mes", "2026-11", "mesmas",
				List.of()), dono);

		assertThat(api.get(adicionais(), dono).objeto()).containsEntry("trazFinal", "NAO");
	}

	@Test
	void pessoaArquivadaNaoRecebeAsCompras() {
		cadastrar("2222", ana);
		assertThat(api.post("/api/pessoas/" + ana + "/arquivar", Map.of(), dono).status()).isLessThan(300);

		api.post("/api/cartoes/" + cartao + "/importacoes", pdf(), dono);

		assertThat(lancamentos()).allSatisfy(l -> assertThat(l.get("pessoaId")).isNull());
	}

	@Test
	void excluirAPessoaLevaOAdicional() {
		cadastrar("2222", ana);

		assertThat(api.delete("/api/pessoas/" + ana, dono).status()).isEqualTo(204);

		assertThat(api.get(adicionais(), dono).objeto().get("adicionais")).isEqualTo(List.of());
	}

}
