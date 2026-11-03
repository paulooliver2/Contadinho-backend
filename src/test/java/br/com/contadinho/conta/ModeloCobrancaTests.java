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
 * HU-21: o modelo da mensagem de cobrança, com as variáveis {nome}, {valor}, {vencimento}, {pix} e {detalhe}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ModeloCobrancaTests {

	private static final String MODELO = "Oi {nome}, {detalhe} total {valor} até {vencimento}. Pix {pix}";

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

	private ClienteTeste.Resposta salvar(String modelo) {
		return api.put("/api/conta/modelo-cobranca", Map.of("modelo", modelo), dono);
	}

	@SuppressWarnings("unchecked")
	private String erroDoModelo(ClienteTeste.Resposta resposta) {
		assertThat(resposta.status()).isEqualTo(400);
		return (String) ((Map<String, Object>) resposta.objeto().get("campos")).get("modelo");
	}

	@Test
	void salvaEDevolveNaConta() {
		var salvo = salvar(MODELO);

		assertThat(salvo.status()).isEqualTo(200);
		assertThat(salvo.objeto()).containsEntry("modelo", MODELO);
		assertThat(api.conta(dono).objeto()).containsEntry("modeloCobranca", MODELO);
	}

	@Test
	void textoVazioVoltaAoPadrao() {
		salvar(MODELO);

		var vazio = salvar("   ");

		assertThat(vazio.status()).isEqualTo(200);
		assertThat(vazio.objeto()).containsEntry("modelo", null);
		assertThat(api.conta(dono).objeto()).containsEntry("modeloCobranca", null);
	}

	@Test
	void variavelDesconhecidaEh400() {
		salvar(MODELO);

		assertThat(erroDoModelo(salvar("Oi {apelido}"))).contains("{apelido}");
		// As variáveis são exatas: com maiúscula é outra.
		assertThat(erroDoModelo(salvar("Oi {Nome}"))).contains("{Nome}");
		assertThat(api.conta(dono).objeto()).containsEntry("modeloCobranca", MODELO);
	}

	@Test
	void chaveSemParEh400() {
		assertThat(erroDoModelo(salvar("Oi {nome"))).isNotBlank();
		assertThat(erroDoModelo(salvar("Oi nome}"))).isNotBlank();
	}

	@Test
	void maisDeMilCaracteresEh400() {
		assertThat(erroDoModelo(salvar("a".repeat(1001)))).isNotBlank();
		assertThat(salvar("a".repeat(1000)).status()).isEqualTo(200);
	}

}
