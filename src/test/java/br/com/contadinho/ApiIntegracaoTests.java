package br.com.contadinho;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sobe a API inteira num Postgres real (RNF-08): migrações, segurança e contrato OpenAPI.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiIntegracaoTests {

	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private HttpResponse<String> get(String path) throws IOException, InterruptedException {
		var uri = URI.create("http://localhost:" + env.getProperty("local.server.port") + path);
		return http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void migracoesCriamAsTabelasDoMvp() {
		var tabelas = jdbc.queryForList(
			"select table_name from information_schema.tables where table_schema = 'public'", String.class);
		assertThat(tabelas).contains("usuario", "cartao", "fatura", "lancamento", "recebivel", "pagamento",
			"conta_fixa", "ocorrencia_conta", "gasto", "receita", "pessoa", "categoria");
	}

	@Test
	void lancamentoNaoSeRepeteNoMesmoCartao() {
		var indices = jdbc.queryForList(
			"select indexdef from pg_indexes where tablename = 'lancamento'", String.class);
		assertThat(indices).anyMatch(def -> def.contains("UNIQUE") && def.contains("(cartao_id, id_externo)"));
	}

	@Test
	void healthEhPublico() throws Exception {
		assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
	}

	@Test
	void apiExigeToken() throws Exception {
		assertThat(get("/api/cartoes").statusCode()).isEqualTo(401);
	}

	@Test
	void appCapacitorPodeChamarAApi() throws Exception {
		var uri = URI.create("http://localhost:" + env.getProperty("local.server.port") + "/api/cartoes");
		var preflight = HttpRequest.newBuilder(uri)
			.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
			.header("Origin", "https://localhost")
			.header("Access-Control-Request-Method", "GET")
			.header("Access-Control-Request-Headers", "authorization")
			.build();
		var resposta = http.send(preflight, HttpResponse.BodyHandlers.discarding());
		assertThat(resposta.headers().firstValue("Access-Control-Allow-Origin")).contains("https://localhost");
	}

	/**
	 * Grava o contrato versionado na raiz do repositório; o app gera o cliente TypeScript a partir dele (RNF-07).
	 * Mudou a API? Rode os testes e faça commit do openapi.json junto. O CI falha se ele estiver desatualizado.
	 */
	@Test
	void publicaContratoOpenApi() throws Exception {
		var resposta = get("/v3/api-docs");
		assertThat(resposta.statusCode()).isEqualTo(200);
		Files.writeString(Path.of("openapi.json"), resposta.body().replace("\r\n", "\n") + "\n");
	}

}
