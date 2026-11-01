package br.com.contadinho.cartoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.time.ZoneId;
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
 * HU-45 e RN-55: editar, encerrar e excluir cartão.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EdicaoCartaoTests {

	/** Um mês adiante: a fatura dele ainda não fechou, qualquer que seja o dia de hoje. */
	private static final YearMonth PROXIMO = YearMonth.now(ZoneId.of("America/Sao_Paulo")).plusMonths(1);

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String cartao;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		cartao = api.post("/api/cartoes", Map.of("apelido", "Inter", "banco", "INTER", "ultimos4", "9453",
				"diaFechamento", 20, "diaVencimento", 28), dono).texto("id");
	}

	private ClienteTeste.Resposta alterar(Object apelido, Object fechamento, Object vencimento) {
		var corpo = new HashMap<String, Object>();
		corpo.put("apelido", apelido);
		corpo.put("diaFechamento", fechamento);
		corpo.put("diaVencimento", vencimento);
		return api.put("/api/cartoes/" + cartao, corpo, dono);
	}

	private void compra() {
		var data = PROXIMO.minusMonths(1).atDay(5).toString();
		assertThat(api.post("/api/cartoes/" + cartao + "/faturas/" + PROXIMO + "/lancamentos",
				Map.of("data", data, "descricao", "Tênis", "valor", 120), dono).status()).isEqualTo(201);
	}

	private Map<String, Object> doCartao() {
		return api.get("/api/cartoes", dono).lista().stream().filter(c -> c.get("id").equals(cartao)).findFirst()
			.orElse(null);
	}

	@Test
	void editaApelidoEDias() {
		var resposta = alterar("Inter da casa", 5, 12);

		assertThat(resposta.status()).isEqualTo(200);
		assertThat(resposta.objeto()).containsEntry("apelido", "Inter da casa").containsEntry("diaFechamento", 5)
			.containsEntry("diaVencimento", 12).containsEntry("banco", "INTER").containsEntry("ultimos4", "9453");
		assertThat(doCartao()).containsEntry("apelido", "Inter da casa");
		assertThat(alterar("  ", 5, 12).objeto()).containsEntry("apelido", "Inter");
	}

	@Test
	void validaOsDias() {
		var resposta = alterar("Inter", 0, 32);

		assertThat(resposta.status()).isEqualTo(400);
		assertThat(resposta.objeto().get("campos")).isEqualTo(
				Map.of("diaFechamento", "Use um dia entre 1 e 31.", "diaVencimento", "Use um dia entre 1 e 31."));
	}

	@Test
	void diasNovosValemSoParaAsProximasFaturas() {
		compra();

		alterar("Inter", 2, 10);

		var gravada = api.get("/api/cartoes/" + cartao + "/faturas/" + PROXIMO, dono).objeto();
		assertThat(gravada.get("vencimento")).isEqualTo(PROXIMO.atDay(28).toString());
		assertThat(gravada.get("fechamento")).isEqualTo(PROXIMO.atDay(20).toString());
		var seguinte = api.get("/api/cartoes/" + cartao + "/faturas/" + PROXIMO.plusMonths(1), dono).objeto();
		assertThat(seguinte.get("vencimento")).isEqualTo(PROXIMO.plusMonths(1).atDay(10).toString());
		assertThat(seguinte.get("fechamento")).isEqualTo(PROXIMO.plusMonths(1).atDay(2).toString());
	}

	@Test
	void semHistoricoExclui() {
		assertThat(doCartao()).containsEntry("podeExcluir", true);

		assertThat(api.delete("/api/cartoes/" + cartao, dono).status()).isEqualTo(204);

		assertThat(doCartao()).isNull();
		assertThat(jdbc.queryForObject("select count(*) from cartao where id = ?::uuid", Integer.class, cartao)).isZero();
	}

	@Test
	void comCompraSoEncerra() {
		compra();
		assertThat(doCartao()).containsEntry("podeExcluir", false);

		var exclusao = api.delete("/api/cartoes/" + cartao, dono);

		assertThat(exclusao.status()).isEqualTo(409);
		assertThat(exclusao.texto("detail")).isEqualTo(
				"Este cartão já tem faturas, contas ou gastos. Encerre-o: o histórico continua nos totais e no A receber.");
	}

	@Test
	void comContaFixaNoCartaoNaoExclui() {
		var categoria = api.get("/api/categorias", dono).lista().stream().filter(c -> c.get("nome").equals("Assinaturas"))
			.map(c -> c.get("id")).findFirst().orElseThrow();
		assertThat(api.post("/api/contas-fixas", Map.of("descricao", "Netflix", "valor", 55.90, "categoriaId", categoria,
				"diaVencimento", 31, "formaPagamento", "CARTAO", "cartaoId", cartao), dono).status()).isEqualTo(201);

		assertThat(doCartao()).containsEntry("podeExcluir", false);
		assertThat(api.delete("/api/cartoes/" + cartao, dono).status()).isEqualTo(409);
	}

	@Test
	void encerradoSomeDaListaEOHistoricoContinua() {
		var lucas = api.post("/api/pessoas", Map.of("nome", "Lucas"), dono).texto("id");
		compra();
		var tenis = api.get("/api/cartoes/" + cartao + "/faturas/" + PROXIMO, dono).objeto();
		@SuppressWarnings("unchecked")
		var lancamento = ((java.util.List<Map<String, Object>>) tenis.get("lancamentos")).getFirst().get("id");
		api.put("/api/lancamentos/" + lancamento + "/responsavel", Map.of("pessoaId", lucas, "proximas", false), dono);

		assertThat(api.post("/api/cartoes/" + cartao + "/encerramento", null, dono).status()).isEqualTo(204);

		assertThat(doCartao()).isNull();
		assertThat(api.get("/api/cartoes/" + cartao + "/faturas/" + PROXIMO, dono).status()).isEqualTo(404);
		assertThat(api.put("/api/cartoes/" + cartao, Map.of("diaFechamento", 1, "diaVencimento", 9), dono).status())
			.isEqualTo(404);
		// O histórico: a fatura entra no resumo do mês e o Lucas segue devendo.
		var resumo = api.get("/api/resumo/mes/" + PROXIMO, dono).objeto().toString();
		assertThat(resumo).contains("Inter •• 9453");
		@SuppressWarnings("unchecked")
		var situacao = (Map<String, Object>) api.get("/api/receber/pessoas/" + lucas, dono).objeto().get("situacao");
		assertThat(situacao.get("saldo")).isEqualTo(120.0);
	}

	@Test
	void cadaDonoSoMexeNosProprios() {
		var outro = api.novoDono();

		assertThat(api.put("/api/cartoes/" + cartao, Map.of("diaFechamento", 1, "diaVencimento", 9), outro).status())
			.isEqualTo(404);
		assertThat(api.post("/api/cartoes/" + cartao + "/encerramento", null, outro).status()).isEqualTo(404);
		assertThat(api.delete("/api/cartoes/" + cartao, outro).status()).isEqualTo(404);
		assertThat(doCartao()).isNotNull();
	}


}
