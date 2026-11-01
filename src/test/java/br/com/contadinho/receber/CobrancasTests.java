package br.com.contadinho.receber;

import static org.assertj.core.api.Assertions.assertThat;

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
 * HU-20.4 contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CobrancasTests {

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String lucas;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		lucas = api.post("/api/pessoas", Map.of("nome", "Lucas"), dono).texto("id");
		api.post("/api/receber/emprestimos", Map.of("pessoaId", lucas, "valor", 300, "parcelas", 1), dono);
	}

	private ClienteTeste.Resposta cobrar(String pessoaId, String token) {
		return api.post("/api/receber/pessoas/" + pessoaId + "/cobrancas", null, token);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> situacao() {
		return (Map<String, Object>) api.get("/api/receber/pessoas/" + lucas, dono).objeto().get("situacao");
	}

	@Test
	@SuppressWarnings("unchecked")
	void guardaQuandoCobrouEOSaldoDaquelaHora() {
		assertThat(situacao()).containsEntry("ultimaCobranca", null);

		var cobranca = cobrar(lucas, dono);

		assertThat(cobranca.status()).isEqualTo(201);
		assertThat(cobranca.objeto()).containsEntry("valor", 300.0);
		var quando = cobranca.texto("enviadaEm");
		assertThat(situacao()).containsEntry("ultimaCobranca", quando);
		var pessoas = (List<Map<String, Object>>) api.get("/api/receber", dono).objeto().get("pessoas");
		assertThat(pessoas.get(0)).containsEntry("ultimaCobranca", quando);
	}

	@Test
	void aUltimaEAMaisRecente() {
		jdbc.update("insert into cobranca (id, usuario_id, pessoa_id, valor, enviada_em) "
				+ "select gen_random_uuid(), usuario_id, id, 300, now() - interval '3 days' from pessoa where id = ?::uuid",
				lucas);
		var hoje = cobrar(lucas, dono).texto("enviadaEm");

		assertThat(situacao()).containsEntry("ultimaCobranca", hoje);
	}

	@Test
	void semSaldoOuDeOutroDonoNaoCobra() {
		api.post("/api/receber/pessoas/" + lucas + "/pagamentos", Map.of("valor", 300), dono);

		assertThat(cobrar(lucas, dono).status()).isEqualTo(409);
		assertThat(cobrar(lucas, api.novoDono()).status()).isEqualTo(404);
	}

}
