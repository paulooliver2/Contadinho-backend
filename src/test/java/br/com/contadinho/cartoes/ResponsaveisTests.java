package br.com.contadinho.cartoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
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
 * HU-08, RN-15 e RN-17 contra um Postgres real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ResponsaveisTests {

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String cartaoId;

	private String lucas;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		cartaoId = api.post("/api/cartoes", Map.of("banco", "ITAU", "ultimos4", "3310", "diaFechamento", 28,
				"diaVencimento", 8), dono).texto("id");
		lucas = api.post("/api/pessoas", Map.of("nome", "Lucas"), dono).texto("id");
	}

	/** Compra digitada na fatura do mês; {@code parcela} como "3/10", ou null à vista. */
	private String digitar(String mes, String descricao, double valor, String parcela) {
		var corpo = new HashMap<String, Object>(Map.of("data", "2026-10-01", "descricao", descricao, "valor", valor));
		if (parcela != null) {
			corpo.put("parcelaAtual", Integer.parseInt(parcela.split("/")[0]));
			corpo.put("parcelaTotal", Integer.parseInt(parcela.split("/")[1]));
		}
		return api.post("/api/cartoes/" + cartaoId + "/faturas/" + mes + "/lancamentos", corpo, dono).texto("id");
	}

	private ClienteTeste.Resposta atribuir(String lancamentoId, String pessoaId, boolean proximas) {
		var corpo = new HashMap<String, Object>();
		corpo.put("pessoaId", pessoaId);
		corpo.put("proximas", proximas);
		return api.put("/api/lancamentos/" + lancamentoId + "/responsavel", corpo, dono);
	}

	/** Descrição e parcela → responsável, na fatura do mês. */
	@SuppressWarnings("unchecked")
	private Map<String, Object> responsaveis(String mes) {
		var lancamentos = (List<Map<String, Object>>) api.get("/api/cartoes/" + cartaoId + "/faturas/" + mes, dono)
			.objeto().get("lancamentos");
		var porCompra = new HashMap<String, Object>();
		for (var l : lancamentos) {
			porCompra.put(l.get("descricao") + " " + l.get("parcelaAtual") + " " + l.get("valor"), l.get("pessoaId"));
		}
		return porCompra;
	}

	@Test
	void compraSemResponsavelEDoDonoEPodeIrParaUmaPessoaEVoltar() {
		var id = digitar("2026-11", "Mercado", 120.5, null);
		assertThat(responsaveis("2026-11")).containsEntry("Mercado null 120.5", null);

		var atribuida = atribuir(id, lucas, false);

		assertThat(atribuida.status()).isEqualTo(200);
		assertThat(atribuida.objeto()).containsEntry("proximas", 0);
		assertThat(responsaveis("2026-11")).containsEntry("Mercado null 120.5", lucas);

		atribuir(id, null, false);

		assertThat(responsaveis("2026-11")).containsEntry("Mercado null 120.5", null);
	}

	@Test
	@SuppressWarnings("unchecked")
	void faturaDivididaSomaOTotalComODonoPrimeiro() {
		var ana = api.post("/api/pessoas", Map.of("nome", "Ana"), dono).texto("id");
		digitar("2026-11", "Mercado", 100.1, null);
		atribuir(digitar("2026-11", "Farmácia", 30.2, null), lucas, false);
		atribuir(digitar("2026-11", "Loja", 250, null), ana, false);
		atribuir(digitar("2026-11", "Estorno Loja", -50, null), ana, false);
		atribuir(digitar("2026-11", "Padaria", 12.35, null), lucas, false);

		var fatura = api.get("/api/cartoes/" + cartaoId + "/faturas/2026-11", dono).objeto();

		assertThat(fatura.get("total")).isEqualTo(342.65);
		var partes = (List<Map<String, Object>>) fatura.get("partes");
		assertThat(partes).extracting(p -> p.get("pessoaId")).containsExactly(null, ana, lucas);
		assertThat(partes).extracting(p -> p.get("total")).containsExactly(100.1, 200.0, 42.55);
		assertThat(partes).extracting(p -> p.get("quantidade")).containsExactly(1, 2, 2);
		assertThat(api.get("/api/cartoes/" + cartaoId + "/faturas/2026-12", dono).objeto().get("partes"))
			.isEqualTo(List.of());
	}

	@Test
	void pessoaArquivadaNaoPodeSerEscolhidaMasACompraDelaContinua() {
		var antes = digitar("2026-11", "Mercado", 120.5, null);
		var estorno = digitar("2026-11", "Estorno Mercado", -120.5, null);
		var depois = digitar("2026-11", "Farmácia", 30, null);
		atribuir(antes, lucas, false);
		atribuir(estorno, lucas, false);
		// Compra e estorno se anulam: saldo zero, então pode arquivar (RN-36).
		assertThat(api.post("/api/pessoas/" + lucas + "/arquivar", null, dono).status()).isEqualTo(200);

		var recusada = atribuir(depois, lucas, false);

		assertThat(recusada.status()).isEqualTo(400);
		assertThat(recusada.objeto()).containsEntry("detail", "Essa pessoa não está na sua lista. Escolha outra.");
		assertThat(atribuir(antes, lucas, false).status()).isEqualTo(200);
	}

	@Test
	void pessoaOuCompraDeOutroDonoNaoServem() {
		var id = digitar("2026-11", "Mercado", 120.5, null);
		var outro = api.novoDono();
		var pessoaDoOutro = api.post("/api/pessoas", Map.of("nome", "Ana"), outro).texto("id");

		assertThat(atribuir(id, pessoaDoOutro, false).status()).isEqualTo(400);
		var corpo = new HashMap<String, Object>();
		corpo.put("pessoaId", null);
		assertThat(api.put("/api/lancamentos/" + id + "/responsavel", corpo, outro).status()).isEqualTo(404);
	}

	@Test
	void proximasParcelasDaMesmaCompraVaoJuntoEChegamAtribuidas() {
		var terceira = digitar("2026-11", "Loja Sofá", 250, "3/10");
		digitar("2026-12", "Loja Sofá", 250, "4/10");
		digitar("2026-12", "Loja Sofá", 99.9, "4/10");
		digitar("2026-12", "Loja Sofá", 250, "2/10");

		var atribuida = atribuir(terceira, lucas, true);

		assertThat(atribuida.objeto()).containsEntry("proximas", 1);
		assertThat(responsaveis("2026-12")).containsEntry("Loja Sofá 4 250.0", lucas)
			.containsEntry("Loja Sofá 4 99.9", null)
			.containsEntry("Loja Sofá 2 250.0", null);

		// A parcela que chega depois já vem com o Lucas, mesmo com centavos de diferença na última.
		digitar("2027-01", "Loja Sofá", 250, "5/10");
		digitar("2027-02", "Loja Sofá", 250.03, "10/10");
		assertThat(responsaveis("2027-01")).containsEntry("Loja Sofá 5 250.0", lucas);
		assertThat(responsaveis("2027-02")).containsEntry("Loja Sofá 10 250.03", lucas);

		// Mudar de ideia atualiza a mesma regra.
		atribuir(terceira, null, true);
		assertThat(responsaveis("2026-12")).containsEntry("Loja Sofá 4 250.0", null);
		assertThat(jdbc.queryForObject("select count(*) from regra_parcela where cartao_id = ?::uuid", Integer.class,
				cartaoId)).isEqualTo(1);
	}

	@Test
	void soEstaParcelaNaoMexeNasProximas() {
		var terceira = digitar("2026-11", "Loja Sofá", 250, "3/10");
		digitar("2026-12", "Loja Sofá", 250, "4/10");

		var atribuida = atribuir(terceira, lucas, false);
		digitar("2027-01", "Loja Sofá", 250, "5/10");

		assertThat(atribuida.objeto()).containsEntry("proximas", 0);
		assertThat(responsaveis("2026-12")).containsEntry("Loja Sofá 4 250.0", null);
		assertThat(responsaveis("2027-01")).containsEntry("Loja Sofá 5 250.0", null);
	}

	@Test
	void parcelaImportadaChegaComOResponsavelDaRegra() {
		importar(ArquivosPdf.inter("12345"), "2026-11");
		@SuppressWarnings("unchecked")
		var novembro = (List<Map<String, Object>>) api.get("/api/cartoes/" + cartaoId + "/faturas/2026-11", dono)
			.objeto().get("lancamentos");
		var setima = novembro.stream().filter(l -> l.get("descricao").toString().startsWith("MERCADOLIVRE")).findFirst()
			.orElseThrow();

		atribuir((String) setima.get("id"), lucas, true);
		importar(ArquivosPdf.pdf("12345", List.of(
				"5364****1111 12/12/2026 R$ 169,98",
				"CARTÃO 5364****2222",
				"21 de mar. 2026 MERCADOLIVRE (Parcela 08 de 10) - R$ 119,98",
				"04 de nov. 2026 MERCADOLIVRE - R$ 50,00",
				"BANCO INTER S/A")), "2026-12");

		assertThat(responsaveis("2026-12")).containsEntry("MERCADOLIVRE (Parcela 08 de 10) 8 119.98", lucas)
			.containsEntry("MERCADOLIVRE null 50.0", null);
	}

	private void importar(byte[] pdf, String mes) {
		var corpo = Map.<String, Object>of("nomeArquivo", "Fatura.pdf", "conteudo",
				Base64.getEncoder().encodeToString(pdf), "senha", "12345", "mes", mes, "mesmas", List.of());
		assertThat(api.post("/api/cartoes/" + cartaoId + "/importacoes", corpo, dono).status()).isEqualTo(201);
	}

}
