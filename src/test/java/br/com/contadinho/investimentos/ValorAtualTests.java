package br.com.contadinho.investimentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

import br.com.contadinho.investimentos.domain.Investimento;
import br.com.contadinho.investimentos.domain.Movimentacao;
import br.com.contadinho.investimentos.domain.Posicao;
import br.com.contadinho.investimentos.domain.ValorAtual;
import org.junit.jupiter.api.Test;

/** HU-31: o valor de hoje pela cotação ou pelo CDI, e o ganho contra o aplicado. */
class ValorAtualTests {

	/** Segunda-feira: a aplicação não rende; terça, quarta e quinta têm CDI. */
	private static final LocalDate APLICACAO = LocalDate.of(2026, 11, 2);

	private static final Instant AGORA = Instant.parse("2026-11-06T12:00:00Z");

	private final UUID usuario = UUID.randomUUID();

	private static BigDecimal n(String valor) {
		return new BigDecimal(valor);
	}

	private static NavigableMap<LocalDate, BigDecimal> cdi(int dias) {
		var mapa = new TreeMap<LocalDate, BigDecimal>();
		for (int i = 1; i <= dias; i++) {
			mapa.put(APLICACAO.plusDays(i), n("0.04"));
		}
		return mapa;
	}

	private Investimento cdb(Investimento.Indexador indexador, String taxa, LocalDate vencimento) {
		return Investimento.rendaFixa(usuario, Investimento.Produto.CDB, "Banco X", indexador, n(taxa), vencimento,
				AGORA);
	}

	private Movimentacao inicial(Investimento i, String valor) {
		return new Movimentacao(i.getId(), usuario, Movimentacao.Tipo.INICIAL, APLICACAO, null, null, n(valor), false,
				AGORA);
	}

	@Test
	void cdbRende110DoCdi() {
		var i = cdb(Investimento.Indexador.CDI, "110", APLICACAO.plusYears(2));

		var r = ValorAtual.rendaFixa(i, List.of(inicial(i, "10000")), cdi(3));

		assertThat(r.valorAtual()).isEqualByComparingTo("10013.21");
		assertThat(r.ganho()).isEqualByComparingTo("13.21");
		assertThat(r.ganhoPercentual()).isEqualByComparingTo("0.13");
		assertThat(r.cdiAte()).isEqualTo(APLICACAO.plusDays(3));
	}

	@Test
	void resgateParcialDepoisDoFator() {
		var i = cdb(Investimento.Indexador.CDI, "110", APLICACAO.plusYears(2));
		var movs = new ArrayList<>(List.of(inicial(i, "10000")));
		movs.add(new Movimentacao(i.getId(), usuario, Movimentacao.Tipo.RESGATE, APLICACAO.plusDays(2), null, null,
				n("5000"), false, AGORA));

		assertThat(ValorAtual.rendaFixa(i, movs, cdi(3)).valorAtual()).isEqualByComparingTo("5011.01");
	}

	@Test
	void prefixadoPorDiaUtil() {
		var i = cdb(Investimento.Indexador.PREFIXADO, "12", APLICACAO.plusYears(2));

		assertThat(ValorAtual.rendaFixa(i, List.of(inicial(i, "10000")), cdi(1)).valorAtual())
			.isEqualByComparingTo("10004.50");
	}

	@Test
	void paraNoVencimento() {
		var i = cdb(Investimento.Indexador.CDI, "110", APLICACAO.plusDays(2));

		var r = ValorAtual.rendaFixa(i, List.of(inicial(i, "10000")), cdi(3));

		assertThat(r.valorAtual()).isEqualByComparingTo("10008.80");
		assertThat(r.cdiAte()).isEqualTo(APLICACAO.plusDays(2));
	}

	@Test
	void saldosPorDiaIgualAoCalculoDeCadaDia() {
		var i = cdb(Investimento.Indexador.CDI, "110", APLICACAO.plusDays(20));
		var cdi = new TreeMap<LocalDate, BigDecimal>();
		for (var d = APLICACAO.minusDays(3); d.isBefore(APLICACAO.plusDays(30)); d = d.plusDays(1)) {
			if (d.getDayOfWeek().getValue() < 6) {
				cdi.put(d, n("0.04"));
			}
		}
		// Resgate parcial num sábado (07/11) e num dia útil (12/11).
		var movs = List.of(inicial(i, "10000"),
				new Movimentacao(i.getId(), usuario, Movimentacao.Tipo.RESGATE, LocalDate.of(2026, 11, 7), null, null,
						n("1000"), false, AGORA),
				new Movimentacao(i.getId(), usuario, Movimentacao.Tipo.RESGATE, LocalDate.of(2026, 11, 12), null, null,
						n("500"), false, AGORA));

		var serie = ValorAtual.saldosPorDia(i, movs, cdi, APLICACAO.plusDays(29));

		for (var d = APLICACAO; !d.isAfter(APLICACAO.plusDays(29)); d = d.plusDays(1)) {
			var dia = d;
			var ate = movs.stream().filter(m -> !m.getData().isAfter(dia)).toList();
			var umDia = ValorAtual.rendaFixa(i, ate, cdi.headMap(dia, true)).valorAtual();
			var esperado = umDia != null ? umDia : Posicao.de(Investimento.Tipo.RENDA_FIXA, ate).aplicado();
			assertThat(serie.floorEntry(dia).getValue()).as(dia.toString()).isEqualByComparingTo(esperado);
		}
	}

	@Test
	void semCdiDepoisDaAplicacao() {
		var i = cdb(Investimento.Indexador.CDI, "110", APLICACAO.plusYears(2));
		var soAntes = new TreeMap<LocalDate, BigDecimal>();
		soAntes.put(APLICACAO.minusDays(1), n("0.04"));

		var r = ValorAtual.rendaFixa(i, List.of(inicial(i, "10000")), soAntes);

		assertThat(r.valorAtual()).isNull();
		assertThat(r.ganho()).isNull();
	}

	@Test
	void mercadoComPreco() {
		var r = ValorAtual.mercado(new Posicao(n("50"), n("35"), n("1750.00"), false), n("38"));

		assertThat(r.valorAtual()).isEqualByComparingTo("1900.00");
		assertThat(r.ganho()).isEqualByComparingTo("150.00");
		assertThat(r.ganhoPercentual()).isEqualByComparingTo("8.57");
		assertThat(r.cdiAte()).isNull();
	}

	@Test
	void mercadoSemPreco() {
		var r = ValorAtual.mercado(new Posicao(n("50"), n("35"), n("1750.00"), false), null);

		assertThat(r.valorAtual()).isNull();
		assertThat(r.ganhoPercentual()).isNull();
	}

	@Test
	void encerradaMostraResultado() {
		var i = cdb(Investimento.Indexador.CDI, "110", APLICACAO.plusYears(2));
		var movs = List.of(inicial(i, "10000"), new Movimentacao(i.getId(), usuario, Movimentacao.Tipo.RESGATE,
				APLICACAO.plusDays(3), null, null, n("10500"), true, AGORA));

		assertThat(ValorAtual.resultadoEncerrada(movs)).isEqualByComparingTo("500.00");
	}

}
