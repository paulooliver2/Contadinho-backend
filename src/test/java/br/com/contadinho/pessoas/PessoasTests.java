package br.com.contadinho.pessoas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
 * HU-16, HU-42, RN-35, RN-36 e RNF-01 contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PessoasTests {

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

	private static Map<String, Object> pessoa(Object... pares) {
		var corpo = new HashMap<String, Object>(Map.of("nome", "Lucas Andrade", "apelido", "Lu", "whatsapp",
				"(61) 99999-8888"));
		for (int i = 0; i < pares.length; i += 2) {
			corpo.put((String) pares[i], pares[i + 1]);
		}
		return corpo;
	}

	private ClienteTeste.Resposta cadastrar(Map<String, Object> corpo) {
		return api.post("/api/pessoas", corpo, dono);
	}

	@Test
	void cadastraEListaEmOrdemDeNome() {
		var lucas = cadastrar(pessoa());
		var ana = cadastrar(pessoa("nome", "Ana", "apelido", null, "whatsapp", null));

		assertThat(lucas.status()).isEqualTo(201);
		assertThat(lucas.objeto()).containsEntry("nome", "Lucas Andrade").containsEntry("apelido", "Lu")
			.containsEntry("whatsapp", "+5561999998888").containsEntry("arquivada", false);
		assertThat(ana.objeto()).containsEntry("apelido", null).containsEntry("whatsapp", null);
		assertThat(api.get("/api/pessoas", dono).lista()).extracting(p -> p.get("nome"))
			.containsExactly("Ana", "Lucas Andrade");
	}

	@Test
	void guardaOWhatsappComMaisCinquentaECinco() {
		assertThat(cadastrar(pessoa("whatsapp", "+55 61 99999-8888")).texto("whatsapp")).isEqualTo("+5561999998888");
		assertThat(cadastrar(pessoa("whatsapp", "0 61 3333-4444")).texto("whatsapp")).isEqualTo("+556133334444");
		assertThat(cadastrar(pessoa("whatsapp", "61999998888")).texto("whatsapp")).isEqualTo("+5561999998888");
		assertThat(cadastrar(pessoa("whatsapp", "  ")).objeto()).containsEntry("whatsapp", null);
	}

	@Test
	void recusaWhatsappSemDddOuComDigitosAMais() {
		for (var numero : new String[] { "99999-8888", "(61) 99999-88889", "+1 415 555 0100", "(01) 99999-8888" }) {
			var resposta = cadastrar(pessoa("whatsapp", numero));

			assertThat(resposta.status()).as(numero).isEqualTo(400);
			assertThat(resposta.objeto().get("campos")).as(numero)
				.isEqualTo(Map.of("whatsapp", "Informe DDD e número, como (61) 99999-8888."));
		}
	}

	@Test
	void exigeONomeELimitaOTamanho() {
		var semNome = cadastrar(pessoa("nome", "  "));
		var apelidoLongo = cadastrar(pessoa("apelido", "x".repeat(41)));

		assertThat(semNome.status()).isEqualTo(400);
		assertThat(semNome.objeto().get("campos")).isEqualTo(Map.of("nome", "Informe o nome."));
		assertThat(apelidoLongo.objeto().get("campos")).isEqualTo(Map.of("apelido", "Use até 40 caracteres."));
	}

	@Test
	void alteraOsDados() {
		var id = cadastrar(pessoa()).texto("id");

		var alterada = api.put("/api/pessoas/" + id, pessoa("nome", "Lucas A.", "apelido", "", "whatsapp", ""), dono);

		assertThat(alterada.status()).isEqualTo(200);
		assertThat(alterada.objeto()).containsEntry("nome", "Lucas A.").containsEntry("apelido", null)
			.containsEntry("whatsapp", null);
	}

	@Test
	void arquivadaSaiDaListaEVoltaAoDesarquivar() {
		var id = cadastrar(pessoa()).texto("id");

		var arquivada = api.post("/api/pessoas/" + id + "/arquivar", null, dono);

		assertThat(arquivada.objeto()).containsEntry("arquivada", true);
		assertThat(api.get("/api/pessoas", dono).lista()).isEmpty();
		assertThat(api.get("/api/pessoas?arquivadas=true", dono).lista()).extracting(p -> p.get("id"))
			.containsExactly(id);

		api.post("/api/pessoas/" + id + "/desarquivar", null, dono);

		assertThat(api.get("/api/pessoas", dono).lista()).extracting(p -> p.get("id")).containsExactly(id);
		assertThat(api.get("/api/pessoas?arquivadas=true", dono).lista()).isEmpty();
	}

	@Test
	void excluiQuemNaoTemHistorico() {
		var id = cadastrar(pessoa()).texto("id");

		assertThat(api.delete("/api/pessoas/" + id, dono).status()).isEqualTo(204);
		assertThat(api.get("/api/pessoas", dono).lista()).isEmpty();
	}

	@Test
	void comCompraAtribuidaNaoExcluiESugereArquivar() {
		var id = cadastrar(pessoa()).texto("id");
		var cartao = api.post("/api/cartoes", Map.of("banco", "ITAU", "ultimos4", "3310", "diaFechamento", 28,
				"diaVencimento", 8), dono);
		var compra = api.post("/api/cartoes/" + cartao.texto("id") + "/faturas/" + cartao.texto("faturaAberta")
				+ "/lancamentos", Map.of("data", "2026-01-02", "descricao", "Mercado", "valor", 50), dono);
		jdbc.update("update lancamento set pessoa_id = ? where id = ?", UUID.fromString(id),
				UUID.fromString(compra.texto("id")));

		var resposta = api.delete("/api/pessoas/" + id, dono);

		assertThat(resposta.status()).isEqualTo(409);
		assertThat(resposta.objeto()).containsEntry("detail",
				"Essa pessoa já tem compras ou valores registrados. Arquive em vez de excluir.");
		assertThat(api.get("/api/pessoas", dono).lista()).hasSize(1);
	}

	@Test
	void pessoaDeOutroDonoNaoAparece() {
		var id = cadastrar(pessoa()).texto("id");
		var outro = api.novoDono();

		assertThat(api.get("/api/pessoas", outro).lista()).isEmpty();
		assertThat(api.put("/api/pessoas/" + id, pessoa(), outro).status()).isEqualTo(404);
		assertThat(api.post("/api/pessoas/" + id + "/arquivar", null, outro).status()).isEqualTo(404);
		assertThat(api.delete("/api/pessoas/" + id, outro).status()).isEqualTo(404);
		assertThat(api.get("/api/pessoas", dono).lista()).hasSize(1);
	}

}
