package br.com.contadinho.investimentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import br.com.contadinho.ClienteTeste;
import br.com.contadinho.TestcontainersConfiguration;
import br.com.contadinho.investimentos.service.FonteCotacoes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** HU-31: atualizar ao abrir, só o que está velho, com a fonte falsa espiada. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AtualizacaoTests {

	private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

	@Autowired
	Environment env;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	FonteCotacoes fonte;

	private ClienteTeste api;

	private String dono;

	@BeforeEach
	void cliente() {
		api = new ClienteTeste(env.getProperty("local.server.port"), jdbc);
		dono = api.novoDono();
		// As cotações são de todos os donos: cada teste começa sem nenhuma.
		jdbc.update("delete from cotacao");
		jdbc.update("delete from cdi_diario");
		jdbc.update("delete from atualizacao_fonte");
		reset(fonte);
	}

	private static BigDecimal n(Object valor) {
		return new BigDecimal(String.valueOf(valor));
	}

	private static LocalDate diaUtilAntesDeHoje() {
		var dia = HOJE.minusDays(1);
		while (dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
			dia = dia.minusDays(1);
		}
		return dia;
	}

	private String criar(Map<String, Object> corpo) {
		var r = api.post("/api/investimentos", corpo, dono);
		assertThat(r.status()).isEqualTo(201);
		return r.texto("id");
	}

	private String acao(String ticker, Object quantidade, Object preco) {
		return criar(Map.of("tipo", "ACAO", "codigo", ticker, "quantidade", quantidade, "preco", preco));
	}

	private String cdb(int diasAtras) {
		return criar(Map.of("tipo", "RENDA_FIXA", "produto", "CDB", "emissor", "Banco X", "valor", 10000,
				"dataAplicacao", HOJE.minusDays(diasAtras).toString(), "indexador", "CDI", "taxa", 110, "vencimento",
				HOJE.plusYears(2).toString()));
	}

	private ClienteTeste.Resposta atualizar(String token) {
		return api.post("/api/investimentos/atualizar", Map.of(), token);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> posicao(ClienteTeste.Resposta carteira, String id) {
		return ((List<Map<String, Object>>) carteira.objeto().get("posicoes")).stream()
			.filter(p -> p.get("id").equals(id))
			.findFirst()
			.orElseThrow();
	}

	@Test
	void getNaoChamaFonte() {
		var id = acao("PETR4", 100, 30);

		var carteira = api.get("/api/investimentos", dono);

		assertThat(posicao(carteira, id).get("valorAtual")).isNull();
		verify(fonte, never()).acoes(anyCollection());
	}

	@Test
	void atualizarTrazPrecoEGanho() {
		var id = acao("PETR4", 100, 30);

		var r = atualizar(dono);

		assertThat(r.status()).isEqualTo(200);
		var p = posicao(r, id);
		assertThat(n(p.get("valorAtual"))).isEqualByComparingTo("3800.00");
		assertThat(n(p.get("ganho"))).isEqualByComparingTo("800.00");
		assertThat(n(p.get("ganhoPercentual"))).isEqualByComparingTo("26.67");
		assertThat(n(p.get("cotacao"))).isEqualByComparingTo("38");
		assertThat(p.get("atualizadoEm")).isNotNull();
		assertThat(p).containsEntry("desatualizado", false);
		assertThat(n(r.objeto().get("patrimonio"))).isEqualByComparingTo("3800.00");
		assertThat(n(r.objeto().get("ganho"))).isEqualByComparingTo("800.00");
		assertThat(n(api.get("/api/investimentos/" + id, dono).objeto().get("valorAtual"))).isEqualByComparingTo("3800");
	}

	@Test
	void nadaVelhoNaoChamaDeNovo() {
		acao("PETR4", 100, 30);

		atualizar(dono);
		atualizar(dono);

		verify(fonte, times(1)).acoes(anyCollection());
	}

	@Test
	void fonteForaMarcaSemCotacao() {
		var id = acao("FORA3", 1, 10);

		var r = atualizar(dono);

		assertThat(r.status()).isEqualTo(200);
		assertThat(posicao(r, id).get("valorAtual")).isNull();
		assertThat(r.objeto()).containsEntry("semCotacao", 1);
	}

	@Test
	void brapiForaCriptoAtualiza() {
		var petr = acao("PETR4", 100, 30);
		atualizar(dono);
		// Passou mais de meia hora desde a última busca.
		jdbc.update("update cotacao set atualizado_em = atualizado_em - interval '1 hour'");
		var btc = criar(Map.of("tipo", "CRIPTO", "codigo", "bitcoin", "quantidade", 0.005, "preco", 350000));
		doThrow(new FonteCotacoes.Indisponivel("brapi", null)).when(fonte).acoes(anyCollection());

		var r = atualizar(dono);

		assertThat(r.status()).isEqualTo(200);
		assertThat(n(posicao(r, btc).get("valorAtual"))).isEqualByComparingTo("2000.00");
		assertThat(posicao(r, petr)).containsEntry("desatualizado", true);
		assertThat(n(posicao(r, petr).get("valorAtual"))).isEqualByComparingTo("3800.00");
	}

	@Test
	void cdbUsaCdi() {
		var id = cdb(10);

		var p = posicao(atualizar(dono), id);

		assertThat(n(p.get("valorAtual"))).isGreaterThan(n("10000"));
		assertThat(p.get("cdiAte")).isEqualTo(diaUtilAntesDeHoje().toString());
		assertThat(p).containsEntry("desatualizado", false);
	}

	@Test
	void cdbMaisAntigoBuscaParaTras() {
		cdb(5);
		atualizar(dono);
		cdb(40);
		clearInvocations(fonte);

		atualizar(dono);

		verify(fonte).cdi(eq(HOJE.minusDays(40)), any());
	}

	@Test
	void duasAtualizacoesAoMesmoTempo() throws Exception {
		acao("PETR4", 100, 30);
		cdb(10);
		var tarefas = new ArrayList<Callable<Integer>>();
		for (int i = 0; i < 2; i++) {
			tarefas.add(() -> atualizar(dono).status());
		}

		try (var executor = Executors.newFixedThreadPool(2)) {
			for (var f : executor.invokeAll(tarefas)) {
				assertThat(f.get()).isEqualTo(200);
			}
		}

		assertThat(jdbc.queryForObject("select count(*) from cotacao where codigo = 'PETR4'", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void cdbQueVenceuNoSabadoNaoFicaDesatualizado() {
		var sabado = HOJE.minusDays(7);
		while (sabado.getDayOfWeek() != DayOfWeek.SATURDAY) {
			sabado = sabado.minusDays(1);
		}
		var id = criar(Map.of("tipo", "RENDA_FIXA", "produto", "CDB", "emissor", "Banco X", "valor", 10000,
				"dataAplicacao", sabado.minusDays(14).toString(), "indexador", "CDI", "taxa", 110, "vencimento",
				sabado.toString()));

		var p = posicao(atualizar(dono), id);

		assertThat(p.get("cdiAte")).isEqualTo(sabado.minusDays(1).toString());
		assertThat(p).containsEntry("desatualizado", false);
	}

	@Test
	void tesouroComODiaAnteriorNaoBaixaAPlanilhaDeNovo() {
		criar(Map.of("tipo", "TESOURO", "tesouroTipo", "IPCA", "tesouroAno", 2035, "quantidade", 1, "valor", 3000));
		jdbc.update("insert into cotacao (id, tipo, codigo, preco, referencia, atualizado_em, fonte) values "
				+ "(gen_random_uuid(), 'TESOURO', 'Tesouro IPCA+ 2035', 3400, ?, now() - interval '1 hour', 'tesouro')",
				java.sql.Date.valueOf(diaUtilAntesDeHoje()));
		jdbc.update("insert into atualizacao_fonte (fonte, tentou_em, falhou) values ('tesouro', now() - interval "
				+ "'1 hour', false)");

		atualizar(dono);

		verify(fonte, never()).tesouro();
	}

	@Test
	void encerradaMostraResultado() {
		var id = cdb(10);
		api.post("/api/investimentos/" + id + "/movimentacoes",
				Map.of("tipo", "RESGATE", "valor", 10500, "total", true), dono);

		var r = atualizar(dono);

		assertThat(n(posicao(r, id).get("resultado"))).isEqualByComparingTo("500.00");
		assertThat(n(posicao(r, id).get("aplicadoInicial"))).isEqualByComparingTo("10000");
		assertThat(n(r.objeto().get("patrimonio"))).isEqualByComparingTo("0");
	}

	@Test
	@SuppressWarnings("unchecked")
	void outroDonoNaoAtualizaOQueNaoEDele() {
		acao("PETR4", 100, 30);
		var outro = api.novoDono();

		var r = atualizar(outro);

		assertThat((List<Object>) r.objeto().get("posicoes")).isEmpty();
		verify(fonte, never()).acoes(anyCollection());
	}

}
