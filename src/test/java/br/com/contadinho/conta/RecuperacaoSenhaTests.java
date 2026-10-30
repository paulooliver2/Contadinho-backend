package br.com.contadinho.conta;

import static br.com.contadinho.conta.ClienteTeste.SENHA;
import static br.com.contadinho.conta.ClienteTeste.emailNovo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.regex.Pattern;

import br.com.contadinho.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * HU-40 e RN-04 contra um Postgres real. O envio de e-mail é trocado por um mock para ler o link.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RecuperacaoSenhaTests {

	private static final String SENHA_NOVA = "outra-senha-456";

	private static final Pattern TOKEN = Pattern.compile("http://app\\.teste/redefinir-senha\\?token=([A-Za-z0-9_-]+)");

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	JavaMailSender carteiro;

	private ClienteTeste api;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
	}

	private ClienteTeste.Resposta pedirLink(String email) {
		return api.post("/api/auth/recuperar-senha", Map.of("email", email));
	}

	private ClienteTeste.Resposta redefinir(String token, String novaSenha) {
		return api.post("/api/auth/redefinir-senha", Map.of("token", token, "novaSenha", novaSenha));
	}

	/** O e-mail sai em segundo plano, depois do commit: espera ele chegar no mock. */
	private SimpleMailMessage emailEnviado() {
		var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(carteiro, timeout(5000)).send(captor.capture());
		return captor.getValue();
	}

	private static String tokenDo(SimpleMailMessage email) {
		var achou = TOKEN.matcher(email.getText());
		assertThat(achou.find()).as("link no e-mail").isTrue();
		return achou.group(1);
	}

	@Test
	void linkPorEmailTrocaASenha() {
		var email = emailNovo();
		api.cadastrar(email);

		assertThat(pedirLink(email.toUpperCase()).status()).isEqualTo(202);
		var enviado = emailEnviado();
		assertThat(enviado.getTo()).containsExactly(email);
		assertThat(enviado.getText()).contains("Olá, Paulo.").contains("30 minutos");

		assertThat(redefinir(tokenDo(enviado), SENHA_NOVA).status()).isEqualTo(204);
		assertThat(api.entrar(email, SENHA).status()).isEqualTo(401);
		assertThat(api.entrar(email, SENHA_NOVA).status()).isEqualTo(200);
	}

	@Test
	void trocarASenhaEncerraTodasAsSessoes() {
		var email = emailNovo();
		var noCelular = api.cadastrar(email).texto("refreshToken");
		var noOutroAparelho = api.entrar(email, SENHA).texto("refreshToken");
		pedirLink(email);

		redefinir(tokenDo(emailEnviado()), SENHA_NOVA);

		assertThat(api.post("/api/auth/renovar", Map.of("refreshToken", noCelular)).status()).isEqualTo(401);
		assertThat(api.post("/api/auth/renovar", Map.of("refreshToken", noOutroAparelho)).status()).isEqualTo(401);
	}

	@Test
	void emailSemContaRecebeAMesmaRespostaENadaEEnviado() {
		var resposta = pedirLink(emailNovo());

		assertThat(resposta.status()).isEqualTo(202);
		verify(carteiro, after(1000).never()).send(any(SimpleMailMessage.class));
	}

	@Test
	void linkSoFuncionaUmaVez() {
		var email = emailNovo();
		api.cadastrar(email);
		pedirLink(email);
		var token = tokenDo(emailEnviado());
		redefinir(token, SENHA_NOVA);

		var deNovo = redefinir(token, "mais-uma-senha-789");

		assertThat(deNovo.status()).isEqualTo(400);
		assertThat(deNovo.texto("detail")).isEqualTo("Este link expirou ou já foi usado. Peça um novo.");
	}

	@Test
	void linkVenceEmTrintaMinutos() {
		var email = emailNovo();
		api.cadastrar(email);
		pedirLink(email);
		var token = tokenDo(emailEnviado());
		jdbc.update("update token_recuperacao set expira_em = now() - interval '1 minute' where token_hash = ?",
				EmissorTokens.hash(token));

		assertThat(redefinir(token, SENHA_NOVA).status()).isEqualTo(400);
		assertThat(api.entrar(email, SENHA).status()).isEqualTo(200);
	}

	@Test
	void senhaNovaSegueAsMesmasRegras() {
		var email = emailNovo();
		api.cadastrar(email);
		pedirLink(email);

		var fraca = redefinir(tokenDo(emailEnviado()), "semnumeros");

		assertThat(fraca.status()).isEqualTo(400);
		assertThat(fraca.corpo().get("campos")).isEqualTo(Map.of("novaSenha", "A senha precisa ter letras e números."));
	}

	@Test
	void pedirDeNovoLogoEmSeguidaNaoMandaOutroEmail() {
		var email = emailNovo();
		api.cadastrar(email);

		pedirLink(email);
		pedirLink(email);

		verify(carteiro, after(1000).times(1)).send(any(SimpleMailMessage.class));
	}

	@Test
	void trocarASenhaTiraOBloqueioDeTentativas() {
		var email = emailNovo();
		api.cadastrar(email);
		api.errosNoPassado(email, 5, 4, 3, 2, 1);
		assertThat(api.entrar(email, SENHA).status()).isEqualTo(429);
		pedirLink(email);

		redefinir(tokenDo(emailEnviado()), SENHA_NOVA);

		assertThat(api.entrar(email, SENHA_NOVA).status()).isEqualTo(200);
	}

}
