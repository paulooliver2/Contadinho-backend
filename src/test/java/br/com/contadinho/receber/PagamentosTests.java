package br.com.contadinho.receber;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
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
 * HU-19, RN-16, RN-29 e RN-30 contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PagamentosTests {

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String cartaoId;

	private String lucas;

	private String ana;

	private String loja;

	private String farmacia;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		cartaoId = api.post("/api/cartoes", Map.of("apelido", "Itaú", "banco", "ITAU", "ultimos4", "3310",
				"diaFechamento", 28, "diaVencimento", 8), dono).texto("id");
		lucas = api.post("/api/pessoas", Map.of("nome", "Lucas"), dono).texto("id");
		ana = api.post("/api/pessoas", Map.of("nome", "Ana"), dono).texto("id");
		// Lucas deve 280,20: a Loja vence em novembro e a Farmácia em dezembro.
		loja = digitar("2026-11", "Loja", 250);
		farmacia = digitar("2026-12", "Farmácia", 30.2);
		atribuir(loja, lucas);
		atribuir(farmacia, lucas);
	}

	private String digitar(String mes, String descricao, double valor) {
		return api.post("/api/cartoes/" + cartaoId + "/faturas/" + mes + "/lancamentos",
				Map.of("data", "2026-10-01", "descricao", descricao, "valor", valor), dono).texto("id");
	}

	private void atribuir(String lancamentoId, String pessoaId) {
		var corpo = new HashMap<String, Object>();
		corpo.put("pessoaId", pessoaId);
		assertThat(api.put("/api/lancamentos/" + lancamentoId + "/responsavel", corpo, dono).status()).isEqualTo(200);
	}

	private ClienteTeste.Resposta pagar(String pessoaId, Object valor) {
		return api.post("/api/receber/pessoas/" + pessoaId + "/pagamentos", Map.of("valor", valor), dono);
	}

	private Map<String, Object> historico(String pessoaId) {
		return api.get("/api/receber/pessoas/" + pessoaId, dono).objeto();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> situacao(String pessoaId) {
		return (Map<String, Object>) historico(pessoaId).get("situacao");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> emAberto(String pessoaId) {
		var compras = (List<Map<String, Object>>) historico(pessoaId).get("compras");
		var porDescricao = new HashMap<String, Object>();
		compras.forEach(c -> porDescricao.put((String) c.get("descricao"), c.get("emAberto")));
		return porDescricao;
	}

	@Test
	void pagamentoParcialAbateDoQueVencePrimeiro() {
		var pago = pagar(lucas, 100);

		assertThat(pago.status()).isEqualTo(201);
		assertThat(pago.objeto()).containsEntry("valor", 100.0).containsEntry("recebidoEm", LocalDate.now().toString());
		assertThat(situacao(lucas)).containsEntry("saldo", 180.2).containsEntry("recebido", 100.0)
			.containsEntry("status", "PAGOU_PARTE").containsEntry("proximoVencimento", "2026-11-08");
		assertThat(emAberto(lucas)).containsEntry("Loja", 150.0).containsEntry("Farmácia", 30.2);

		// Quitada a Loja, o próximo vencimento passa a ser o da Farmácia (RN-30).
		pagar(lucas, 150);

		assertThat(situacao(lucas)).containsEntry("saldo", 30.2).containsEntry("proximoVencimento", "2026-12-08");
		assertThat(emAberto(lucas)).containsEntry("Loja", 0.0).containsEntry("Farmácia", 30.2);
		var resumo = api.get("/api/receber", dono).objeto();
		assertThat(resumo).containsEntry("aReceber", 30.2).containsEntry("recebido", 250.0);
	}

	@Test
	void pagarOSaldoInteiroDeixaComoPagoELiberaOArquivamento() {
		pagar(lucas, 280.2);

		assertThat(situacao(lucas)).containsEntry("saldo", 0.0).containsEntry("status", "PAGO")
			.containsEntry("proximoVencimento", null);
		assertThat(api.post("/api/pessoas/" + lucas + "/arquivar", null, dono).status()).isEqualTo(200);
	}

	@Test
	void valorMaiorQueODevidoEDataFuturaSaoRecusados() {
		var maior = pagar(lucas, 280.21);

		assertThat(maior.status()).isEqualTo(400);
		assertThat(maior.objeto().get("campos")).isEqualTo(Map.of("valor", "Valor maior que o devido."));
		assertThat(pagar(lucas, 0).status()).isEqualTo(400);
		assertThat(pagar(lucas, 10.001).status()).isEqualTo(400);
		var futura = api.post("/api/receber/pessoas/" + lucas + "/pagamentos",
				Map.of("valor", 10, "recebidoEm", LocalDate.now().plusDays(2).toString()), dono);
		assertThat(futura.objeto().get("campos")).isEqualTo(Map.of("recebidoEm", "A data não pode ser depois de hoje."));
		// Ana não deve nada.
		assertThat(pagar(ana, 10).status()).isEqualTo(409);
		assertThat(situacao(lucas)).containsEntry("recebido", 0);
	}

	@Test
	void desfazerVoltaOSaldo() {
		var id = pagar(lucas, 100).texto("id");

		assertThat(api.delete("/api/receber/pagamentos/" + id, api.novoDono()).status()).isEqualTo(404);
		assertThat(api.delete("/api/receber/pagamentos/" + id, dono).status()).isEqualTo(204);

		assertThat(situacao(lucas)).containsEntry("saldo", 280.2).containsEntry("status", "PENDENTE");
		assertThat(historico(lucas).get("pagamentos")).isEqualTo(List.of());
		assertThat(api.delete("/api/receber/pagamentos/" + id, dono).status()).isEqualTo(404);
		// Fica guardado como desfeito.
		assertThat(jdbc.queryForObject("select desfeito_em is not null from pagamento where id = ?::uuid",
				Boolean.class, id)).isTrue();
	}

	@Test
	@SuppressWarnings("unchecked")
	void historicoListaOsPagamentosDoMaisRecente() {
		var ontem = LocalDate.now().minusDays(1).toString();
		api.post("/api/receber/pessoas/" + lucas + "/pagamentos", Map.of("valor", 50, "recebidoEm", ontem), dono);
		pagar(lucas, 20);

		var pagamentos = (List<Map<String, Object>>) historico(lucas).get("pagamentos");

		assertThat(pagamentos).extracting(p -> p.get("valor")).containsExactly(20.0, 50.0);
		assertThat(pagamentos.get(1)).containsEntry("recebidoEm", ontem);
	}

	@Test
	void trocarOResponsavelDeUmaCompraPagaViraCredito() {
		pagar(lucas, 250);

		// A Loja, já paga, vai para a Ana: o pagamento fica com o Lucas como crédito (RN-16).
		atribuir(loja, ana);

		assertThat(situacao(lucas)).containsEntry("saldo", -219.8).containsEntry("status", "PAGO");
		assertThat(emAberto(lucas)).containsEntry("Farmácia", 0.0);
		assertThat(situacao(ana)).containsEntry("saldo", 250.0).containsEntry("status", "PENDENTE");
		// Crédito não soma no total a receber, e com ele não há o que pagar.
		assertThat(api.get("/api/receber", dono).objeto()).containsEntry("aReceber", 250.0);
		assertThat(pagar(lucas, 1).status()).isEqualTo(409);
	}

	@Test
	void pessoaDeOutroDonoNaoRecebePagamento() {
		var outro = api.novoDono();

		assertThat(api.post("/api/receber/pessoas/" + lucas + "/pagamentos", Map.of("valor", 10), outro).status())
			.isEqualTo(404);
	}

}
