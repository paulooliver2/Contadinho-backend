package br.com.contadinho.cartoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
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
 * HU-05, RN-07, RN-08 e RNF-01 contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CartoesTests {

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

	private static Map<String, Object> cartao(Object... pares) {
		var corpo = new HashMap<String, Object>(Map.of("apelido", "Itaú da casa", "banco", "ITAU", "ultimos4", "3310",
				"diaFechamento", 28, "diaVencimento", 8));
		for (int i = 0; i < pares.length; i += 2) {
			corpo.put((String) pares[i], pares[i + 1]);
		}
		return corpo;
	}

	private ClienteTeste.Resposta cadastrar(Map<String, Object> corpo) {
		return api.post("/api/cartoes", corpo, dono);
	}

	@Test
	void cadastraEListaOCartao() {
		var novo = cadastrar(cartao());

		assertThat(novo.status()).isEqualTo(201);
		assertThat(novo.objeto()).containsEntry("apelido", "Itaú da casa").containsEntry("banco", "ITAU")
			.containsEntry("bancoNome", "Itaú").containsEntry("ultimos4", "3310")
			.containsEntry("diaFechamento", 28).containsEntry("diaVencimento", 8);
		var lista = api.get("/api/cartoes", dono);
		assertThat(lista.lista()).extracting(c -> c.get("id")).containsExactly(novo.texto("id"));
	}

	@Test
	void semApelidoLevaONomeDoBanco() {
		var itau = cadastrar(cartao("apelido", "  "));
		var outro = cadastrar(cartao("apelido", null, "banco", "OUTRO"));

		assertThat(itau.texto("apelido")).isEqualTo("Itaú");
		assertThat(outro.texto("apelido")).isEqualTo("Cartão");
	}

	@Test
	void podeTerVariosCartoesDoMesmoBanco() {
		cadastrar(cartao("ultimos4", "1111"));
		cadastrar(cartao("ultimos4", "1111"));

		assertThat(api.get("/api/cartoes", dono).lista()).hasSize(2);
	}

	@Test
	void soAceitaOsQuatroUltimosDigitos() {
		var numeroCompleto = cadastrar(cartao("ultimos4", "4111111111111111"));
		var comLetra = cadastrar(cartao("ultimos4", "12a4"));

		assertThat(numeroCompleto.status()).isEqualTo(400);
		assertThat(numeroCompleto.objeto().get("campos")).isEqualTo(Map.of("ultimos4", "Informe os 4 últimos dígitos do cartão."));
		assertThat(comLetra.status()).isEqualTo(400);
		assertThat(jdbc.queryForObject("select count(*) from cartao where ultimos4 like '41%'", Integer.class)).isZero();
	}

	@Test
	void diasVaoDe1A31() {
		var zero = cadastrar(cartao("diaFechamento", 0));
		var trintaEDois = cadastrar(cartao("diaVencimento", 32));
		var trintaEUm = cadastrar(cartao("diaFechamento", 31, "diaVencimento", 1));

		assertThat(zero.objeto().get("campos")).isEqualTo(Map.of("diaFechamento", "Use um dia entre 1 e 31."));
		assertThat(trintaEDois.objeto().get("campos")).isEqualTo(Map.of("diaVencimento", "Use um dia entre 1 e 31."));
		assertThat(trintaEUm.status()).isEqualTo(201);
	}

	@Test
	void bancoForaDaListaERecusado() {
		var resposta = cadastrar(cartao("banco", "BANCO_FANTASMA"));

		assertThat(resposta.status()).isEqualTo(400);
		assertThat(resposta.texto("detail")).isEqualTo("Não deu para entender os dados enviados. Confira e tente de novo.");
	}

	@Test
	void cadaDonoSoVeOsPropriosCartoes() {
		cadastrar(cartao());
		var outroDono = api.novoDono();

		assertThat(api.get("/api/cartoes", outroDono).lista()).isEmpty();
	}

	@Test
	void listaDeBancosTrazOModeloDeImportacao() {
		var bancos = api.get("/api/cartoes/bancos", dono).lista();

		assertThat(bancos).hasSize(11);
		assertThat(bancos.getFirst()).isEqualTo(Map.of("codigo", "NUBANK", "nome", "Nubank", "modelo", "BETA"));
		assertThat(bancos.getLast()).isEqualTo(Map.of("codigo", "OUTRO", "nome", "Outro", "modelo", "SEM_MODELO"));
	}

	@Test
	void exigeLogin() {
		assertThat(api.post("/api/cartoes", cartao(), null).status()).isEqualTo(401);
	}

}
