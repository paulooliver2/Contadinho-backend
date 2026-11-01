package br.com.contadinho.conta;

import static org.assertj.core.api.Assertions.assertThat;

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
 * HU-03 contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChavePixContaTests {

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

	@Test
	void semChaveAContaNaoTrazPix() {
		assertThat(api.conta(dono).objeto()).containsEntry("pix", null);
	}

	@Test
	void salvaNormalizadaETroca() {
		var salva = api.put("/api/conta/pix", Map.of("tipo", "CELULAR", "chave", "(61) 99999-8888"), dono);

		assertThat(salva.status()).isEqualTo(200);
		assertThat(salva.objeto()).containsEntry("tipo", "CELULAR").containsEntry("chave", "+5561999998888");

		api.put("/api/conta/pix", Map.of("tipo", "CPF", "chave", "529.982.247-25"), dono);

		assertThat(api.conta(dono).objeto().get("pix")).isEqualTo(Map.of("tipo", "CPF", "chave", "52998224725"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void foraDoFormatoDoTipoERecusada() {
		var recusada = api.put("/api/conta/pix", Map.of("tipo", "CPF", "chave", "123.456.789-00"), dono);

		assertThat(recusada.status()).isEqualTo(400);
		assertThat((Map<String, Object>) recusada.objeto().get("campos"))
			.containsEntry("chave", "Confira o CPF: são 11 números, como 123.456.789-09.");
		assertThat(api.put("/api/conta/pix", Map.of("tipo", "BOLETO", "chave", "x"), dono).status()).isEqualTo(400);
		assertThat(api.conta(dono).objeto()).containsEntry("pix", null);
	}

	@Test
	void removeAChave() {
		api.put("/api/conta/pix", Map.of("tipo", "EMAIL", "chave", "voce@email.com"), dono);

		assertThat(api.delete("/api/conta/pix", dono).status()).isEqualTo(204);

		assertThat(api.conta(dono).objeto()).containsEntry("pix", null);
	}

}
