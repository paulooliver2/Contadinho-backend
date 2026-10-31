package br.com.contadinho.receber;

import static org.assertj.core.api.Assertions.assertThat;

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
 * HU-17, RN-31, RN-32 e RN-40 contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmprestimosTests {

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String lucas;

	private String ana;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		lucas = api.post("/api/pessoas", Map.of("nome", "Lucas"), dono).texto("id");
		ana = api.post("/api/pessoas", Map.of("nome", "Ana"), dono).texto("id");
	}

	private ClienteTeste.Resposta emprestar(String pessoaId, Object valor, int parcelas, String primeira) {
		var corpo = new HashMap<String, Object>(Map.of("pessoaId", pessoaId, "descricao", "Conserto do carro",
				"valor", valor, "parcelas", parcelas));
		corpo.put("primeiraDevolucao", primeira);
		return api.post("/api/receber/emprestimos", corpo, dono);
	}

	private Map<String, Object> historico(String pessoaId) {
		return api.get("/api/receber/pessoas/" + pessoaId, dono).objeto();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> situacao(String pessoaId) {
		return (Map<String, Object>) historico(pessoaId).get("situacao");
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> parcelas(String pessoaId) {
		var emprestimos = (List<Map<String, Object>>) historico(pessoaId).get("emprestimos");
		return (List<Map<String, Object>>) emprestimos.get(0).get("parcelas");
	}

	@Test
	@SuppressWarnings("unchecked")
	void parcelasMensaisComOsCentavosNaUltima() {
		var criado = emprestar(lucas, 100, 3, "2026-11-10");

		assertThat(criado.status()).isEqualTo(201);
		var parcelas = (List<Map<String, Object>>) criado.objeto().get("parcelas");
		assertThat(parcelas).extracting(p -> p.get("valor")).containsExactly(33.33, 33.33, 33.34);
		assertThat(parcelas).extracting(p -> p.get("vencimento"))
			.containsExactly("2026-11-10", "2026-12-10", "2027-01-10");
		assertThat(situacao(lucas)).containsEntry("saldo", 100.0).containsEntry("status", "PENDENTE")
			.containsEntry("proximoVencimento", "2026-11-10");
		var emprestimo = ((List<Map<String, Object>>) historico(lucas).get("emprestimos")).get(0);
		assertThat(emprestimo).containsEntry("descricao", "Conserto do carro").containsEntry("valor", 100.0);
	}

	@Test
	void fimDoMesViraOUltimoDiaDosMesesMaisCurtos() {
		emprestar(lucas, 300, 3, "2027-01-31");

		assertThat(parcelas(lucas)).extracting(p -> p.get("vencimento"))
			.containsExactly("2027-01-31", "2027-02-28", "2027-03-31");
	}

	@Test
	@SuppressWarnings("unchecked")
	void semDataNaoTemVencimentoEFicaDepoisDeQuemTem() {
		emprestar(ana, 50, 2, null);
		emprestar(lucas, 80, 1, "2026-12-01");

		assertThat(parcelas(ana)).extracting(p -> p.get("vencimento")).containsExactly(null, null);
		assertThat(situacao(ana)).containsEntry("saldo", 50.0).containsEntry("proximoVencimento", null);
		var pessoas = (List<Map<String, Object>>) api.get("/api/receber", dono).objeto().get("pessoas");
		assertThat(pessoas).extracting(p -> p.get("pessoaId")).containsExactly(lucas, ana);
	}

	@Test
	void pagamentoAbateDaParcelaQueVencePrimeiro() {
		emprestar(lucas, 600, 3, "2026-11-10");

		api.post("/api/receber/pessoas/" + lucas + "/pagamentos", Map.of("valor", 250), dono);

		assertThat(parcelas(lucas)).extracting(p -> p.get("emAberto")).containsExactly(0.0, 150.0, 200.0);
		assertThat(situacao(lucas)).containsEntry("saldo", 350.0).containsEntry("proximoVencimento", "2026-12-10");
	}

	@Test
	@SuppressWarnings("unchecked")
	void regrasDoRegistro() {
		assertThat((Map<String, Object>) emprestar(lucas, 100, 4, null).objeto().get("campos"))
			.containsEntry("parcelas", "Escolha 1, 2, 3 ou 6 parcelas.");
		assertThat((Map<String, Object>) emprestar(lucas, 0.05, 6, null).objeto().get("campos"))
			.containsEntry("valor", "Valor pequeno demais para tantas parcelas.");
		assertThat(emprestar(lucas, 0, 1, null).status()).isEqualTo(400);

		var outroDono = api.post("/api/pessoas", Map.of("nome", "Bia"), api.novoDono()).texto("id");
		assertThat((Map<String, Object>) emprestar(outroDono, 10, 1, null).objeto().get("campos"))
			.containsEntry("pessoaId", "Essa pessoa não está na sua lista. Escolha outra.");
		api.post("/api/pessoas/" + ana + "/arquivar", null, dono);
		assertThat(emprestar(ana, 10, 1, null).status()).isEqualTo(400);

		assertThat(jdbc.queryForObject("select count(*) from emprestimo where pessoa_id = ?::uuid", Integer.class,
				lucas)).isZero();
	}

	@Test
	void excluirApagaAsParcelas() {
		var id = emprestar(lucas, 100, 3, "2026-11-10").texto("id");

		assertThat(api.delete("/api/receber/emprestimos/" + id, api.novoDono()).status()).isEqualTo(404);
		assertThat(api.delete("/api/receber/emprestimos/" + id, dono).status()).isEqualTo(204);

		assertThat(situacao(lucas)).containsEntry("saldo", 0);
		assertThat(historico(lucas).get("emprestimos")).isEqualTo(List.of());
		assertThat(jdbc.queryForObject("select count(*) from recebivel where emprestimo_id = ?::uuid", Integer.class,
				id)).isZero();
	}

	@Test
	void pessoaComEmprestimoNaoEExcluida() {
		emprestar(lucas, 100, 1, null);

		assertThat(api.delete("/api/pessoas/" + lucas, dono).status()).isEqualTo(409);
	}

}
