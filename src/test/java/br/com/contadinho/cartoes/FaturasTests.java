package br.com.contadinho.cartoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.contadinho.ClienteTeste;
import br.com.contadinho.TestcontainersConfiguration;
import br.com.contadinho.cartoes.service.FaturaServico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * HU-47, RN-58, RN-60 e RNF-01 contra um Postgres real. O cartão fecha dia 28 e vence dia 8, então a fatura
 * de novembro de 2026 fecha em 28/10.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FaturasTests {

	private static final String NOVEMBRO = "2026-11";

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	private ClienteTeste api;

	private String dono;

	private String cartaoId;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		cartaoId = api.post("/api/cartoes", Map.of("banco", "ITAU", "ultimos4", "3310", "diaFechamento", 28,
				"diaVencimento", 8), dono).texto("id");
	}

	private static Map<String, Object> compra(Object... pares) {
		var corpo = new HashMap<String, Object>(Map.of("data", "2026-10-03", "descricao", "Mercado Bom Preço", "valor", 120.50));
		for (int i = 0; i < pares.length; i += 2) {
			corpo.put((String) pares[i], pares[i + 1]);
		}
		return corpo;
	}

	private String fatura(String mes) {
		return "/api/cartoes/" + cartaoId + "/faturas/" + mes;
	}

	private ClienteTeste.Resposta adicionar(Map<String, Object> corpo) {
		return api.post(fatura(NOVEMBRO) + "/lancamentos", corpo, dono);
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> lancamentos(String mes) {
		return (List<Map<String, Object>>) api.get(fatura(mes), dono).objeto().get("lancamentos");
	}

	@Test
	void faturaSemComprasTemAsDatasDoCartao() {
		var fatura = api.get(fatura(NOVEMBRO), dono);

		assertThat(fatura.status()).isEqualTo(200);
		assertThat(fatura.objeto()).containsEntry("mes", NOVEMBRO).containsEntry("fechamento", "2026-10-28")
			.containsEntry("vencimento", "2026-11-08").containsEntry("total", 0).containsEntry("lancamentos", List.of());
	}

	@Test
	void cartaoTrazOMesDaFaturaAberta() {
		var hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
		var esperado = hoje.getDayOfMonth() <= 28 ? hoje.plusMonths(1) : hoje.plusMonths(2);

		var cartao = api.get("/api/cartoes", dono).lista().getFirst();

		assertThat(cartao.get("faturaAberta")).isEqualTo(esperado.toString().substring(0, 7));
	}

	@Test
	void compraDigitadaEntraNaFaturaENoTotal() {
		var nova = adicionar(compra());
		adicionar(compra("data", "2026-09-15", "descricao", "Loja de Móveis", "valor", 300, "parcelaAtual", 2, "parcelaTotal", 5));

		assertThat(nova.status()).isEqualTo(201);
		assertThat(nova.objeto()).containsEntry("descricao", "Mercado Bom Preço").containsEntry("valor", 120.5)
			.containsEntry("origem", "MANUAL").containsEntry("parcelaAtual", null);
		assertThat(api.get(fatura(NOVEMBRO), dono).objeto().get("total")).isEqualTo(420.5);
		assertThat(lancamentos(NOVEMBRO)).extracting(l -> l.get("descricao"))
			.containsExactly("Mercado Bom Preço", "Loja de Móveis");
		assertThat(jdbc.queryForObject("select descricao_normalizada from lancamento where id = ?::uuid", String.class,
				nova.texto("id"))).isEqualTo("MERCADO BOM PRECO");
	}

	@Test
	void estornoDigitadoAbateOTotal() {
		adicionar(compra());
		adicionar(compra("descricao", "Estorno mercado", "valor", -20.50));

		assertThat(api.get(fatura(NOVEMBRO), dono).objeto().get("total")).isEqualTo(100.0);
	}

	@Test
	void dataDepoisDoFechamentoERecusada() {
		var resposta = adicionar(compra("data", "2026-10-29"));

		assertThat(resposta.status()).isEqualTo(400);
		assertThat(resposta.objeto().get("campos"))
			.isEqualTo(Map.of("data", "A fatura fecha em 28/10; use uma data até lá."));
	}

	@Test
	void valorEParcelaSeguemARn60() {
		var zero = adicionar(compra("valor", 0));
		var tresCasas = adicionar(compra("valor", 10.123));
		var semTotal = adicionar(compra("parcelaAtual", 3));
		var passaDoTotal = adicionar(compra("parcelaAtual", 11, "parcelaTotal", 10));
		var semDescricao = adicionar(compra("descricao", "   "));

		assertThat(zero.objeto().get("campos")).isEqualTo(Map.of("valor", "Informe um valor diferente de zero."));
		assertThat(tresCasas.objeto().get("campos")).isEqualTo(Map.of("valor", "Use no máximo 2 casas decimais."));
		assertThat(semTotal.objeto().get("campos")).isEqualTo(Map.of("parcela", "Informe a parcela e o total, como 3/10."));
		assertThat(passaDoTotal.objeto().get("campos")).isEqualTo(Map.of("parcela", "A parcela não pode passar do total."));
		assertThat(semDescricao.objeto().get("campos")).isEqualTo(Map.of("descricao", "Informe a descrição."));
		assertThat(jdbc.queryForObject("select count(*) from lancamento where cartao_id = ?::uuid", Integer.class, cartaoId)).isZero();
	}

	@Test
	void compraDigitadaPodeSerCorrigidaEExcluida() {
		var id = adicionar(compra()).texto("id");

		var corrigida = api.put("/api/lancamentos/" + id, compra("descricao", "Mercado Central", "valor", 99.90,
				"parcelaAtual", 1, "parcelaTotal", 2), dono);
		assertThat(corrigida.status()).isEqualTo(200);
		assertThat(corrigida.objeto()).containsEntry("descricao", "Mercado Central").containsEntry("valor", 99.9)
			.containsEntry("parcelaAtual", 1).containsEntry("parcelaTotal", 2);

		assertThat(api.delete("/api/lancamentos/" + id, dono).status()).isEqualTo(204);
		assertThat(api.get(fatura(NOVEMBRO), dono).objeto()).containsEntry("total", 0).containsEntry("lancamentos", List.of());
	}

	@Test
	void compraDoArquivoNaoPodeSerCorrigidaNemExcluida() {
		var id = adicionar(compra()).texto("id");
		var faturaId = jdbc.queryForObject("select fatura_id from lancamento where id = ?::uuid", UUID.class, id);
		var importacao = UUID.randomUUID();
		jdbc.update("insert into importacao (id, usuario_id, fatura_id, nome_arquivo, formato, modelo) "
				+ "select ?, usuario_id, id, 'fatura.ofx', 'OFX', 'generico' from fatura where id = ?", importacao, faturaId);
		jdbc.update("update lancamento set origem = 'ARQUIVO', importacao_id = ?, id_externo = 'FITID-1' where id = ?::uuid",
				importacao, id);

		var corrigir = api.put("/api/lancamentos/" + id, compra(), dono);
		var excluir = api.delete("/api/lancamentos/" + id, dono);

		assertThat(corrigir.status()).isEqualTo(409);
		assertThat(corrigir.texto("detail")).isEqualTo(FaturaServico.COMPRA_DO_ARQUIVO);
		assertThat(excluir.status()).isEqualTo(409);
	}

	@Test
	void cadaDonoSoVeAsPropriasFaturas() {
		var id = adicionar(compra()).texto("id");
		var outroDono = api.novoDono();

		assertThat(api.get(fatura(NOVEMBRO), outroDono).status()).isEqualTo(404);
		assertThat(api.post(fatura(NOVEMBRO) + "/lancamentos", compra(), outroDono).status()).isEqualTo(404);
		assertThat(api.put("/api/lancamentos/" + id, compra("valor", 1), outroDono).status()).isEqualTo(404);
		assertThat(api.delete("/api/lancamentos/" + id, outroDono).status()).isEqualTo(404);
		assertThat(api.get(fatura(NOVEMBRO), dono).objeto().get("total")).isEqualTo(120.5);
	}

	@Test
	void mesInvalidoERecusado() {
		var resposta = api.get(fatura("2026-13"), dono);

		assertThat(resposta.status()).isEqualTo(400);
		assertThat(resposta.texto("detail")).isEqualTo("Endereço inválido. Volte e tente de novo.");
	}

}
