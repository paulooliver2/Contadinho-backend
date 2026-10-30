package br.com.contadinho.conta;

import static br.com.contadinho.ClienteTeste.SENHA;
import static br.com.contadinho.ClienteTeste.emailNovo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import br.com.contadinho.ClienteTeste;
import br.com.contadinho.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * HU-01 de ponta a ponta, contra um Postgres real: cadastro, login, bloqueio (RN-03), senha (RN-02) e sessões.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AutenticacaoTests {

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
	}

	@Test
	void cadastroJaEntraNoApp() {
		var email = emailNovo();
		var cadastro = api.post("/api/auth/cadastro", Map.of("nome", " Paulo ", "email", " " + email.toUpperCase() + " ", "senha", SENHA));

		assertThat(cadastro.status()).isEqualTo(201);
		assertThat(cadastro.texto("refreshToken")).isNotBlank();
		var eu = api.conta(cadastro.texto("tokenAcesso"));
		assertThat(eu.status()).isEqualTo(200);
		assertThat(eu.objeto()).containsEntry("nome", "Paulo").containsEntry("email", email);
	}

	@Test
	void tokenDeAcessoValeQuinzeMinutos() {
		var cadastro = api.cadastrar(emailNovo());

		var expira = Instant.parse(cadastro.texto("acessoExpiraEm"));
		assertThat(expira).isBetween(Instant.now().plus(14, ChronoUnit.MINUTES), Instant.now().plus(16, ChronoUnit.MINUTES));
	}

	@Test
	void naoCadastraOMesmoEmailDuasVezes() {
		var email = emailNovo();
		api.cadastrar(email);

		var deNovo = api.cadastrar(email.toUpperCase());

		assertThat(deNovo.status()).isEqualTo(409);
		assertThat(deNovo.texto("detail")).contains("Já existe uma conta");
	}

	@Test
	void senhaPrecisaDeDezCaracteresComLetrasENumeros() {
		var soLetras = api.post("/api/auth/cadastro", Map.of("nome", "Paulo", "email", emailNovo(), "senha", "abcdefghijkl"));
		var curta = api.post("/api/auth/cadastro", Map.of("nome", "Paulo", "email", emailNovo(), "senha", "abc123"));

		assertThat(soLetras.status()).isEqualTo(400);
		assertThat(soLetras.objeto().get("campos")).isEqualTo(Map.of("senha", "A senha precisa ter letras e números."));
		assertThat(curta.status()).isEqualTo(400);
		assertThat(curta.objeto().get("campos")).isEqualTo(Map.of("senha", "A senha precisa ter de 10 a 64 caracteres."));
	}

	@Test
	void entraComEmailESenha() {
		var email = emailNovo();
		api.cadastrar(email);

		var login = api.entrar(email.toUpperCase(), SENHA);

		assertThat(login.status()).isEqualTo(200);
		assertThat(api.conta(login.texto("tokenAcesso")).status()).isEqualTo(200);
	}

	@Test
	void senhaErradaEEmailInexistenteDaoAMesmaResposta() {
		var email = emailNovo();
		api.cadastrar(email);

		var senhaErrada = api.entrar(email, "outra-senha-1");
		var semConta = api.entrar(emailNovo(), SENHA);

		assertThat(senhaErrada.status()).isEqualTo(401);
		assertThat(semConta.status()).isEqualTo(401);
		assertThat(senhaErrada.texto("detail")).isEqualTo(semConta.texto("detail")).isEqualTo("E-mail ou senha incorretos.");
	}

	@Test
	void cincoErrosEmQuinzeMinutosBloqueiam() {
		var email = emailNovo();
		api.cadastrar(email);
		for (int i = 0; i < 4; i++) {
			assertThat(api.entrar(email, "errada-" + i + "abc").status()).isEqualTo(401);
		}

		var quinto = api.entrar(email, "errada-5abc");
		var comASenhaCerta = api.entrar(email, SENHA);

		assertThat(quinto.status()).isEqualTo(429);
		assertThat(quinto.texto("detail")).isEqualTo("Muitas tentativas com senha errada. Tente de novo em 15 minutos.");
		assertThat(quinto.cabecalhos().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
		assertThat(comASenhaCerta.status()).isEqualTo(429);
	}

	@Test
	void bloqueioAcabaDepoisDeQuinzeMinutos() {
		var email = emailNovo();
		api.cadastrar(email);
		api.errosNoPassado(email, 20, 19, 18, 17, 16);

		assertThat(api.entrar(email, SENHA).status()).isEqualTo(200);
	}

	@Test
	void errosEspalhadosEmMaisDeQuinzeMinutosNaoBloqueiam() {
		var email = emailNovo();
		api.cadastrar(email);
		api.errosNoPassado(email, 29, 25, 20, 14);

		assertThat(api.entrar(email, "errada-5abc").status()).isEqualTo(401);
	}

	@Test
	void loginCertoZeraOsErros() {
		var email = emailNovo();
		api.cadastrar(email);
		api.errosNoPassado(email, 4, 3, 2, 1);
		assertThat(api.entrar(email, SENHA).status()).isEqualTo(200);

		assertThat(api.entrar(email, "errada-5abc").status()).isEqualTo(401);
	}

	@Test
	void bloqueioValeTambemParaEmailSemConta() {
		var email = emailNovo();
		api.errosNoPassado(email, 4, 3, 2, 1);

		assertThat(api.entrar(email, SENHA).status()).isEqualTo(429);
	}

	@Test
	void renovarTrocaORefreshToken() {
		var cadastro = api.cadastrar(emailNovo());
		var antigo = cadastro.texto("refreshToken");

		var renovado = api.post("/api/auth/renovar", Map.of("refreshToken", antigo));
		var reuso = api.post("/api/auth/renovar", Map.of("refreshToken", antigo));

		assertThat(renovado.status()).isEqualTo(200);
		assertThat(renovado.texto("refreshToken")).isNotEqualTo(antigo);
		assertThat(api.conta(renovado.texto("tokenAcesso")).status()).isEqualTo(200);
		assertThat(reuso.status()).isEqualTo(401);
		assertThat(api.post("/api/auth/renovar", Map.of("refreshToken", renovado.texto("refreshToken"))).status()).isEqualTo(200);
	}

	@Test
	void refreshTokenExpiradoVoltaParaOLogin() {
		var cadastro = api.cadastrar(emailNovo());
		var refresh = cadastro.texto("refreshToken");
		jdbc.update("update sessao set expira_em = now() - interval '1 minute' where token_hash = ?", EmissorTokens.hash(refresh));

		var renovar = api.post("/api/auth/renovar", Map.of("refreshToken", refresh));

		assertThat(renovar.status()).isEqualTo(401);
		assertThat(renovar.texto("detail")).isEqualTo("Sua sessão terminou. Entre de novo.");
	}

	@Test
	void sairEncerraASessao() {
		var refresh = api.cadastrar(emailNovo()).texto("refreshToken");

		var sair = api.post("/api/auth/sair", Map.of("refreshToken", refresh));

		assertThat(sair.status()).isEqualTo(204);
		assertThat(api.post("/api/auth/renovar", Map.of("refreshToken", refresh)).status()).isEqualTo(401);
	}

	@Test
	void refreshTokenNaoFicaGuardadoNoBanco() {
		var refresh = api.cadastrar(emailNovo()).texto("refreshToken");

		var comOToken = jdbc.queryForObject("select count(*) from sessao where token_hash = ?", Integer.class, refresh);
		var comOHash = jdbc.queryForObject("select count(*) from sessao where token_hash = ?", Integer.class, EmissorTokens.hash(refresh));

		assertThat(comOToken).isZero();
		assertThat(comOHash).isOne();
	}

}
