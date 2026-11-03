package br.com.contadinho.conta;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
 * HU-04: apagar a conta confirmando com a senha. Tudo do dono sai do banco na hora (RN-56); os outros donos não mudam.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApagarContaTests {

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
	}

	/** Uma conta com cartão, compra atribuída a uma pessoa e um gasto. */
	private record Dono(String email, String token, String refresh, UUID id) {
	}

	private Dono donoComDados() {
		var email = ClienteTeste.emailNovo();
		var cadastro = api.cadastrar(email);
		var token = cadastro.texto("tokenAcesso");
		var id = UUID.fromString(api.conta(token).texto("id"));
		var pessoa = api.post("/api/pessoas", Map.of("nome", "Lucas Andrade", "apelido", "Lucas", "whatsapp",
				"61999998888"), token).texto("id");
		var cartao = api.post("/api/cartoes", Map.of("banco", "ITAU", "ultimos4", "3310", "diaFechamento", 28,
				"diaVencimento", 8), token);
		var compra = api.post("/api/cartoes/" + cartao.texto("id") + "/faturas/" + cartao.texto("faturaAberta")
				+ "/lancamentos", Map.of("data", "2026-01-02", "descricao", "Mercado", "valor", 50), token).texto("id");
		var responsavel = new HashMap<String, Object>(Map.of("pessoaId", pessoa, "proximas", false));
		assertThat(api.put("/api/lancamentos/" + compra + "/responsavel", responsavel, token).status()).isEqualTo(200);
		var mercado = api.get("/api/categorias", token).lista().stream()
			.filter(c -> c.get("nome").equals("Mercado"))
			.map(c -> (String) c.get("id"))
			.findFirst()
			.orElseThrow();
		var gasto = new HashMap<String, Object>(Map.of("descricao", "Feira", "valor", 86, "categoriaId", mercado,
				"formaPagamento", "PIX_BOLETO"));
		assertThat(api.post("/api/gastos", gasto, token).status()).isEqualTo(201);
		return new Dono(email, token, cadastro.texto("refreshToken"), id);
	}

	private ClienteTeste.Resposta apagar(Dono dono, String senha) {
		return api.delete("/api/conta", Map.of("senha", senha), dono.token());
	}

	private List<String> tabelasDoDono() {
		return jdbc.queryForList("select table_name from information_schema.columns where column_name = 'usuario_id'"
				+ " and table_schema = 'public' order by table_name", String.class);
	}

	/** Linhas de cada tabela com usuario_id, mais a própria linha em usuario. */
	private Map<String, Integer> linhas(UUID usuarioId) {
		var contagem = new LinkedHashMap<String, Integer>();
		for (var tabela : tabelasDoDono()) {
			contagem.put(tabela, jdbc.queryForObject("select count(*) from " + tabela + " where usuario_id = ?",
					Integer.class, usuarioId));
		}
		contagem.put("usuario", jdbc.queryForObject("select count(*) from usuario where id = ?", Integer.class,
				usuarioId));
		return contagem;
	}

	@Test
	void apagaTudoDoDonoENadaDosOutros() {
		var a = donoComDados();
		var b = donoComDados();
		var antesDeB = linhas(b.id());
		assertThat(linhas(a.id()).values()).anyMatch(n -> n > 0);

		assertThat(apagar(a, ClienteTeste.SENHA).status()).isEqualTo(204);

		assertThat(linhas(a.id())).allSatisfy((tabela, n) -> assertThat(n).as(tabela).isZero());
		assertThat(linhas(b.id())).isEqualTo(antesDeB);
	}

	@Test
	void sessoesMorrem() {
		var a = donoComDados();

		apagar(a, ClienteTeste.SENHA);

		assertThat(api.post("/api/auth/renovar", Map.of("refreshToken", a.refresh())).status()).isEqualTo(401);
		assertThat(api.conta(a.token()).status()).isIn(401, 404);
	}

	/** Revisão: outro aparelho com o token de acesso ainda válido (até 15 min) recebe 401, não 500, ao gravar. */
	@Test
	void outroAparelhoGravandoRecebe401() {
		var a = donoComDados();

		apagar(a, ClienteTeste.SENHA);

		assertThat(api.post("/api/pessoas", Map.of("nome", "Bia Souza", "apelido", "Bia", "whatsapp", "61988887777"),
				a.token()).status()).isEqualTo(401);
		assertThat(api.post("/api/cartoes", Map.of("banco", "INTER", "ultimos4", "1111", "diaFechamento", 20,
				"diaVencimento", 28), a.token()).status()).isEqualTo(401);
	}

	@Test
	void naoEntraMaisEMesmoEmailRecomeca() {
		var a = donoComDados();

		apagar(a, ClienteTeste.SENHA);

		assertThat(api.entrar(a.email(), ClienteTeste.SENHA).status()).isEqualTo(401);
		var nova = api.cadastrar(a.email());
		assertThat(nova.status()).isEqualTo(201);
		assertThat(api.get("/api/cartoes", nova.texto("tokenAcesso")).lista()).isEmpty();
		assertThat(api.get("/api/pessoas", nova.texto("tokenAcesso")).lista()).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void senhaErradaNaoApagaEConta() {
		var a = donoComDados();

		var errada = apagar(a, "senha-errada-1");

		assertThat(errada.status()).isEqualTo(400);
		assertThat((Map<String, Object>) errada.objeto().get("campos")).containsEntry("senha", "Senha incorreta.");
		assertThat(linhas(a.id()).get("usuario")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from tentativa_login where email = ? and not sucesso",
				Integer.class, a.email())).isEqualTo(1);

		// RN-03: com 4 erros recentes, o 5º bloqueia.
		api.errosNoPassado(a.email(), 3, 2, 1);
		assertThat(apagar(a, "senha-errada-1").status()).isEqualTo(429);
		assertThat(apagar(a, ClienteTeste.SENHA).status()).isEqualTo(429);
		assertThat(linhas(a.id()).get("usuario")).isEqualTo(1);
	}

	@Test
	void tentativasDoEmailSaem() {
		var a = donoComDados();
		api.errosNoPassado(a.email(), 20);

		apagar(a, ClienteTeste.SENHA);

		assertThat(jdbc.queryForObject("select count(*) from tentativa_login where email = ?", Integer.class,
				a.email())).isZero();
	}

	@Test
	void semTokenEh401() {
		var r = api.delete("/api/conta", Map.of("senha", ClienteTeste.SENHA), null);

		assertThat(r.status()).isEqualTo(401);
	}

}
