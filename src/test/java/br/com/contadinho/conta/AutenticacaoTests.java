package br.com.contadinho.conta;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import br.com.contadinho.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * HU-01 de ponta a ponta, contra um Postgres real: cadastro, login, bloqueio (RN-03), senha (RN-02) e sessões.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AutenticacaoTests {

	private static final String SENHA = "senha-boa-123";

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private RestClient http;

	record Resposta(int status, Map<String, Object> corpo, HttpHeaders cabecalhos) {

		String texto(String campo) {
			return (String) corpo.get(campo);
		}
	}

	@BeforeEach
	void cliente() {
		http = RestClient.builder()
			.baseUrl("http://localhost:" + env.getProperty("local.server.port"))
			.defaultStatusHandler(status -> true, (requisicao, resposta) -> {
			})
			.build();
	}

	private Resposta post(String caminho, Map<String, ?> corpo) {
		var r = http.post().uri(caminho).contentType(MediaType.APPLICATION_JSON).body(corpo)
			.retrieve().toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		return new Resposta(r.getStatusCode().value(), r.getBody(), r.getHeaders());
	}

	private Resposta conta(String tokenAcesso) {
		var r = http.get().uri("/api/conta").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAcesso)
			.retrieve().toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		return new Resposta(r.getStatusCode().value(), r.getBody(), r.getHeaders());
	}

	private static String emailNovo() {
		return "dono-" + UUID.randomUUID() + "@exemplo.com";
	}

	private Resposta cadastrar(String email) {
		return post("/api/auth/cadastro", Map.of("nome", "Paulo", "email", email, "senha", SENHA));
	}

	private Resposta entrar(String email, String senha) {
		return post("/api/auth/entrar", Map.of("email", email, "senha", senha));
	}

	private void errosNoPassado(String email, int... minutosAtras) {
		for (int minutos : minutosAtras) {
			jdbc.update("insert into tentativa_login (email, sucesso, ocorrida_em) values (?, false, ?)", email,
					Instant.now().minus(minutos, ChronoUnit.MINUTES).atOffset(ZoneOffset.UTC));
		}
	}

	@Test
	void cadastroJaEntraNoApp() {
		var email = emailNovo();
		var cadastro = post("/api/auth/cadastro", Map.of("nome", " Paulo ", "email", " " + email.toUpperCase() + " ", "senha", SENHA));

		assertThat(cadastro.status()).isEqualTo(201);
		assertThat(cadastro.texto("refreshToken")).isNotBlank();
		var eu = conta(cadastro.texto("tokenAcesso"));
		assertThat(eu.status()).isEqualTo(200);
		assertThat(eu.corpo()).containsEntry("nome", "Paulo").containsEntry("email", email);
	}

	@Test
	void tokenDeAcessoValeQuinzeMinutos() {
		var cadastro = cadastrar(emailNovo());

		var expira = Instant.parse(cadastro.texto("acessoExpiraEm"));
		assertThat(expira).isBetween(Instant.now().plus(14, ChronoUnit.MINUTES), Instant.now().plus(16, ChronoUnit.MINUTES));
	}

	@Test
	void naoCadastraOMesmoEmailDuasVezes() {
		var email = emailNovo();
		cadastrar(email);

		var deNovo = cadastrar(email.toUpperCase());

		assertThat(deNovo.status()).isEqualTo(409);
		assertThat(deNovo.texto("detail")).contains("Já existe uma conta");
	}

	@Test
	void senhaPrecisaDeDezCaracteresComLetrasENumeros() {
		var soLetras = post("/api/auth/cadastro", Map.of("nome", "Paulo", "email", emailNovo(), "senha", "abcdefghijkl"));
		var curta = post("/api/auth/cadastro", Map.of("nome", "Paulo", "email", emailNovo(), "senha", "abc123"));

		assertThat(soLetras.status()).isEqualTo(400);
		assertThat(soLetras.corpo().get("campos")).isEqualTo(Map.of("senha", "A senha precisa ter letras e números."));
		assertThat(curta.status()).isEqualTo(400);
		assertThat(curta.corpo().get("campos")).isEqualTo(Map.of("senha", "A senha precisa ter de 10 a 64 caracteres."));
	}

	@Test
	void entraComEmailESenha() {
		var email = emailNovo();
		cadastrar(email);

		var login = entrar(email.toUpperCase(), SENHA);

		assertThat(login.status()).isEqualTo(200);
		assertThat(conta(login.texto("tokenAcesso")).status()).isEqualTo(200);
	}

	@Test
	void senhaErradaEEmailInexistenteDaoAMesmaResposta() {
		var email = emailNovo();
		cadastrar(email);

		var senhaErrada = entrar(email, "outra-senha-1");
		var semConta = entrar(emailNovo(), SENHA);

		assertThat(senhaErrada.status()).isEqualTo(401);
		assertThat(semConta.status()).isEqualTo(401);
		assertThat(senhaErrada.texto("detail")).isEqualTo(semConta.texto("detail")).isEqualTo("E-mail ou senha incorretos.");
	}

	@Test
	void cincoErrosEmQuinzeMinutosBloqueiam() {
		var email = emailNovo();
		cadastrar(email);
		for (int i = 0; i < 4; i++) {
			assertThat(entrar(email, "errada-" + i + "abc").status()).isEqualTo(401);
		}

		var quinto = entrar(email, "errada-5abc");
		var comASenhaCerta = entrar(email, SENHA);

		assertThat(quinto.status()).isEqualTo(429);
		assertThat(quinto.texto("detail")).isEqualTo("Muitas tentativas com senha errada. Tente de novo em 15 minutos.");
		assertThat(quinto.cabecalhos().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
		assertThat(comASenhaCerta.status()).isEqualTo(429);
	}

	@Test
	void bloqueioAcabaDepoisDeQuinzeMinutos() {
		var email = emailNovo();
		cadastrar(email);
		errosNoPassado(email, 20, 19, 18, 17, 16);

		assertThat(entrar(email, SENHA).status()).isEqualTo(200);
	}

	@Test
	void errosEspalhadosEmMaisDeQuinzeMinutosNaoBloqueiam() {
		var email = emailNovo();
		cadastrar(email);
		errosNoPassado(email, 29, 25, 20, 14);

		assertThat(entrar(email, "errada-5abc").status()).isEqualTo(401);
	}

	@Test
	void loginCertoZeraOsErros() {
		var email = emailNovo();
		cadastrar(email);
		errosNoPassado(email, 4, 3, 2, 1);
		assertThat(entrar(email, SENHA).status()).isEqualTo(200);

		assertThat(entrar(email, "errada-5abc").status()).isEqualTo(401);
	}

	@Test
	void bloqueioValeTambemParaEmailSemConta() {
		var email = emailNovo();
		errosNoPassado(email, 4, 3, 2, 1);

		assertThat(entrar(email, SENHA).status()).isEqualTo(429);
	}

	@Test
	void renovarTrocaORefreshToken() {
		var cadastro = cadastrar(emailNovo());
		var antigo = cadastro.texto("refreshToken");

		var renovado = post("/api/auth/renovar", Map.of("refreshToken", antigo));
		var reuso = post("/api/auth/renovar", Map.of("refreshToken", antigo));

		assertThat(renovado.status()).isEqualTo(200);
		assertThat(renovado.texto("refreshToken")).isNotEqualTo(antigo);
		assertThat(conta(renovado.texto("tokenAcesso")).status()).isEqualTo(200);
		assertThat(reuso.status()).isEqualTo(401);
		assertThat(post("/api/auth/renovar", Map.of("refreshToken", renovado.texto("refreshToken"))).status()).isEqualTo(200);
	}

	@Test
	void refreshTokenExpiradoVoltaParaOLogin() {
		var cadastro = cadastrar(emailNovo());
		var refresh = cadastro.texto("refreshToken");
		jdbc.update("update sessao set expira_em = now() - interval '1 minute' where token_hash = ?", EmissorTokens.hash(refresh));

		var renovar = post("/api/auth/renovar", Map.of("refreshToken", refresh));

		assertThat(renovar.status()).isEqualTo(401);
		assertThat(renovar.texto("detail")).isEqualTo("Sua sessão terminou. Entre de novo.");
	}

	@Test
	void sairEncerraASessao() {
		var refresh = cadastrar(emailNovo()).texto("refreshToken");

		var sair = post("/api/auth/sair", Map.of("refreshToken", refresh));

		assertThat(sair.status()).isEqualTo(204);
		assertThat(post("/api/auth/renovar", Map.of("refreshToken", refresh)).status()).isEqualTo(401);
	}

	@Test
	void refreshTokenNaoFicaGuardadoNoBanco() {
		var refresh = cadastrar(emailNovo()).texto("refreshToken");

		var comOToken = jdbc.queryForObject("select count(*) from sessao where token_hash = ?", Integer.class, refresh);
		var comOHash = jdbc.queryForObject("select count(*) from sessao where token_hash = ?", Integer.class, EmissorTokens.hash(refresh));

		assertThat(comOToken).isZero();
		assertThat(comOHash).isOne();
	}

}
