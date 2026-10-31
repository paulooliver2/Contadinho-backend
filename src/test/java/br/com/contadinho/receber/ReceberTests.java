package br.com.contadinho.receber;

import static org.assertj.core.api.Assertions.assertThat;

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
 * HU-18, RN-16, RN-29 e RN-36 contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReceberTests {

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String cartaoId;

	private String lucas;

	private String ana;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		cartaoId = api.post("/api/cartoes", Map.of("apelido", "Itaú", "banco", "ITAU", "ultimos4", "3310",
				"diaFechamento", 28, "diaVencimento", 8), dono).texto("id");
		lucas = api.post("/api/pessoas", Map.of("nome", "Lucas"), dono).texto("id");
		ana = api.post("/api/pessoas", Map.of("nome", "Ana"), dono).texto("id");
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

	private Map<String, Object> resumo() {
		return api.get("/api/receber", dono).objeto();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> situacao(String pessoaId) {
		return ((List<Map<String, Object>>) resumo().get("pessoas")).stream()
			.filter(p -> p.get("pessoaId").equals(pessoaId))
			.findFirst()
			.orElse(null);
	}

	@Test
	void compraAtribuidaViraValorAReceberLigadoAFatura() {
		atribuir(digitar("2026-12", "Farmácia", 30.2), lucas);
		atribuir(digitar("2026-12", "Padaria", 12.35), lucas);
		atribuir(digitar("2026-11", "Loja", 250), ana);
		digitar("2026-11", "Mercado", 100);

		var resumo = resumo();

		assertThat(resumo).containsEntry("aReceber", 292.55).containsEntry("recebido", 0);
		@SuppressWarnings("unchecked")
		var pessoas = (List<Map<String, Object>>) resumo.get("pessoas");
		// Ana primeiro: a fatura dela vence antes (HU-18.2).
		assertThat(pessoas).extracting(p -> p.get("pessoaId")).containsExactly(ana, lucas);
		assertThat(pessoas.get(1)).containsEntry("saldo", 42.55).containsEntry("original", 42.55)
			.containsEntry("status", "PENDENTE").containsEntry("proximoVencimento", "2026-12-08");
	}

	@Test
	void recebivelAcompanhaACompra() {
		var farmacia = digitar("2026-12", "Farmácia", 30.2);
		var padaria = digitar("2026-12", "Padaria", 12.35);
		atribuir(farmacia, lucas);
		atribuir(padaria, lucas);

		// Trocar de pessoa move; corrigir o valor acompanha; voltar ao dono e excluir a compra tiram.
		atribuir(farmacia, ana);
		api.put("/api/lancamentos/" + padaria, Map.of("data", "2026-10-01", "descricao", "Padaria", "valor", 20), dono);
		assertThat(situacao(ana)).containsEntry("saldo", 30.2);
		assertThat(situacao(lucas)).containsEntry("saldo", 20.0);

		atribuir(farmacia, null);
		api.delete("/api/lancamentos/" + padaria, dono);

		assertThat(resumo()).containsEntry("aReceber", 0).containsEntry("pessoas", List.of());
		assertThat(jdbc.queryForObject("select count(*) from recebivel where pessoa_id in (?::uuid, ?::uuid)", Integer.class,
				lucas, ana)).isZero();
	}

	@Test
	void estornoAbateEQuemZeraFicaComoPago() {
		atribuir(digitar("2026-12", "Loja", 250), ana);
		atribuir(digitar("2026-12", "Estorno Loja", -250), ana);

		assertThat(situacao(ana)).containsEntry("saldo", 0.0).containsEntry("status", "PAGO")
			.containsEntry("proximoVencimento", null);
		assertThat(resumo()).containsEntry("aReceber", 0);
	}

	@Test
	void parcelaQueChegaComARegraJaTemRecebivel() {
		var terceira = api.post("/api/cartoes/" + cartaoId + "/faturas/2026-11/lancamentos", Map.of("data",
				"2026-10-01", "descricao", "Sofá", "valor", 250, "parcelaAtual", 3, "parcelaTotal", 10), dono).texto("id");
		var corpo = new HashMap<String, Object>(Map.of("pessoaId", lucas, "proximas", true));
		api.put("/api/lancamentos/" + terceira + "/responsavel", corpo, dono);

		api.post("/api/cartoes/" + cartaoId + "/faturas/2026-12/lancamentos", Map.of("data", "2026-10-01",
				"descricao", "Sofá", "valor", 250, "parcelaAtual", 4, "parcelaTotal", 10), dono);

		assertThat(situacao(lucas)).containsEntry("saldo", 500.0).containsEntry("proximoVencimento", "2026-11-08");
	}

	@Test
	@SuppressWarnings("unchecked")
	void historicoMostraAsComprasDaPessoa() {
		atribuir(digitar("2026-11", "Loja", 250), lucas);
		atribuir(digitar("2026-12", "Farmácia", 30.2), lucas);
		atribuir(digitar("2026-12", "Padaria", 12), ana);

		var historico = api.get("/api/receber/pessoas/" + lucas, dono);

		assertThat(historico.status()).isEqualTo(200);
		assertThat((Map<String, Object>) historico.objeto().get("situacao")).containsEntry("saldo", 280.2);
		var compras = (List<Map<String, Object>>) historico.objeto().get("compras");
		assertThat(compras).extracting(c -> c.get("descricao")).containsExactly("Farmácia", "Loja");
		assertThat(compras.get(0)).containsEntry("cartao", "Itaú •• 3310").containsEntry("fatura", "2026-12");
	}

	@Test
	void pessoaSemNadaTemHistoricoVazioEDeOutroDonoNaoAparece() {
		var vazio = api.get("/api/receber/pessoas/" + ana, dono).objeto();
		assertThat(vazio.get("compras")).isEqualTo(List.of());
		assertThat(api.get("/api/receber/pessoas/" + ana, api.novoDono()).status()).isEqualTo(404);
	}

	@Test
	void comSaldoNaoArquiva() {
		var loja = digitar("2026-12", "Loja", 250);
		atribuir(loja, ana);

		var recusado = api.post("/api/pessoas/" + ana + "/arquivar", null, dono);

		assertThat(recusado.status()).isEqualTo(409);
		assertThat(recusado.objeto()).containsEntry("detail",
				"Ana tem saldo de R$ 250,00 a receber. Só dá para arquivar com o saldo zerado.");
		atribuir(loja, null);
		assertThat(api.post("/api/pessoas/" + ana + "/arquivar", null, dono).status()).isEqualTo(200);
	}

}
