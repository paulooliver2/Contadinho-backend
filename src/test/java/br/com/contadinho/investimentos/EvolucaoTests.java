package br.com.contadinho.investimentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

import br.com.contadinho.investimentos.domain.Evolucao;
import br.com.contadinho.investimentos.domain.Investimento;
import br.com.contadinho.investimentos.domain.Movimentacao;
import org.junit.jupiter.api.Test;

/** HU-32: o valor de cada dia e o rendimento do período, descontados aportes e resgates (RN-49). */
class EvolucaoTests {

	/** Uma sexta-feira. */
	private static final LocalDate HOJE = LocalDate.of(2026, 11, 6);

	private static final Instant AGORA = Instant.parse("2026-11-06T12:00:00Z");

	private final UUID usuario = UUID.randomUUID();

	private static BigDecimal n(Object valor) {
		return new BigDecimal(String.valueOf(valor));
	}

	private static LocalDate dia(int atras) {
		return HOJE.minusDays(atras);
	}

	private Investimento acao(String ticker) {
		return new Investimento(usuario, Investimento.Tipo.ACAO, ticker, ticker, ticker, true, AGORA);
	}

	private Movimentacao mov(Investimento i, Movimentacao.Tipo tipo, int atras, Object quantidade, Object preco) {
		var q = n(quantidade);
		var p = preco == null ? null : n(preco);
		return new Movimentacao(i.getId(), usuario, tipo, dia(atras), q, p, p == null ? null : q.multiply(p), false,
				AGORA);
	}

	private static NavigableMap<LocalDate, BigDecimal> precos(Object... diasEPrecos) {
		var mapa = new TreeMap<LocalDate, BigDecimal>();
		for (int k = 0; k < diasEPrecos.length; k += 2) {
			mapa.put(dia((Integer) diasEPrecos[k]), n(diasEPrecos[k + 1]));
		}
		return mapa;
	}

	private static NavigableMap<LocalDate, BigDecimal> semCdi() {
		return new TreeMap<>();
	}

	/** 0,04% em todo dia de semana de 400 dias atrás até ontem. */
	private static NavigableMap<LocalDate, BigDecimal> cdiAteOntem() {
		var cdi = new TreeMap<LocalDate, BigDecimal>();
		for (var d = HOJE.minusDays(400); d.isBefore(HOJE); d = d.plusDays(1)) {
			if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
				cdi.put(d, n("0.04"));
			}
		}
		return cdi;
	}

	private static BigDecimal ponto(Evolucao.Resultado r, int atras) {
		return r.pontos().stream().filter(p -> p.data().equals(dia(atras))).findFirst().orElseThrow().valor();
	}

	private Evolucao.Ativo petrComAporte() {
		var i = acao("PETR4");
		return new Evolucao.Ativo(i,
				List.of(mov(i, Movimentacao.Tipo.INICIAL, 20, 10, 30), mov(i, Movimentacao.Tipo.APORTE, 10, 10, 33)),
				precos(20, 30, 10, 33), n(36));
	}

	@Test
	void aporteNaoContaComoRendimento() {
		var r = Evolucao.calcular(Evolucao.Periodo.MES, HOJE, List.of(petrComAporte()), semCdi());

		assertThat(r.desdeCadastro()).isTrue();
		assertThat(r.de()).isEqualTo(dia(20));
		assertThat(r.aportes()).isEqualByComparingTo("630.00");
		assertThat(r.valorFinal()).isEqualByComparingTo("720.00");
		assertThat(r.ganho()).isEqualByComparingTo("90.00");
		assertThat(r.ganhoPercentual()).isEqualByComparingTo("14.29");
		assertThat(ponto(r, 15)).isEqualByComparingTo("300.00");
		assertThat(ponto(r, 10)).isEqualByComparingTo("660.00");
		assertThat(r.pontos().getFirst().data()).isEqualTo(dia(20));
	}

	@Test
	void diaComparaComOntem() {
		var i = acao("PETR4");
		var a = new Evolucao.Ativo(i, List.of(mov(i, Movimentacao.Tipo.INICIAL, 40, 10, 30)), precos(1, 35), n(36));

		var r = Evolucao.calcular(Evolucao.Periodo.DIA, HOJE, List.of(a), semCdi());

		assertThat(r.pontos()).extracting(Evolucao.Ponto::data).containsExactly(dia(1), HOJE);
		assertThat(ponto(r, 1)).isEqualByComparingTo("350.00");
		assertThat(ponto(r, 0)).isEqualByComparingTo("360.00");
		assertThat(r.ganho()).isEqualByComparingTo("10.00");
		assertThat(r.ganhoPercentual()).isEqualByComparingTo("2.86");
		assertThat(r.desdeCadastro()).isFalse();
	}

	@Test
	void resgateNaoEPerda() {
		var i = acao("PETR4");
		var a = new Evolucao.Ativo(i,
				List.of(mov(i, Movimentacao.Tipo.INICIAL, 40, 10, 30), mov(i, Movimentacao.Tipo.RESGATE, 5, 5, 30)),
				precos(40, 30), n(30));

		var r = Evolucao.calcular(Evolucao.Periodo.MES, HOJE, List.of(a), semCdi());

		assertThat(r.valorInicial()).isEqualByComparingTo("300.00");
		assertThat(r.resgates()).isEqualByComparingTo("150.00");
		assertThat(r.ganho()).isEqualByComparingTo("0.00");
		assertThat(r.ganhoPercentual()).isEqualByComparingTo("0.00");
	}

	@Test
	void semPrecoAntesUsaPrecoMedio() {
		var i = acao("PETR4");
		var a = new Evolucao.Ativo(i, List.of(mov(i, Movimentacao.Tipo.INICIAL, 20, 10, 30)), precos(5, 40), null);

		var r = Evolucao.calcular(Evolucao.Periodo.MES, HOJE, List.of(a), semCdi());

		assertThat(ponto(r, 10)).isEqualByComparingTo("300.00");
	}

	@Test
	void semCotacaoNuncaFicaFora() {
		var fora = acao("FORA3");
		var semPreco = new Evolucao.Ativo(fora, List.of(mov(fora, Movimentacao.Tipo.INICIAL, 15, 1, 10)), semCdi(),
				null);

		var r = Evolucao.calcular(Evolucao.Periodo.MES, HOJE, List.of(petrComAporte(), semPreco), semCdi());

		assertThat(r.aportes()).isEqualByComparingTo("630.00");
		assertThat(r.valorFinal()).isEqualByComparingTo("720.00");
	}

	@Test
	void hojeUsaCotacaoAtual() {
		var i = acao("PETR4");
		var a = new Evolucao.Ativo(i, List.of(mov(i, Movimentacao.Tipo.INICIAL, 40, 10, 30)), precos(3, 35), n(36));

		var r = Evolucao.calcular(Evolucao.Periodo.MES, HOJE, List.of(a), semCdi());

		assertThat(r.valorFinal()).isEqualByComparingTo("360.00");
	}

	@Test
	void rendaFixaEncerradaValeZeroDepois() {
		var i = Investimento.rendaFixa(usuario, Investimento.Produto.CDB, "Banco X", Investimento.Indexador.CDI,
				n(110), HOJE.plusYears(2), AGORA);
		var inicial = new Movimentacao(i.getId(), usuario, Movimentacao.Tipo.INICIAL, dia(30), null, null, n(10000),
				false, AGORA);
		var resgate = new Movimentacao(i.getId(), usuario, Movimentacao.Tipo.RESGATE, dia(10), null, null, n(10090),
				true, AGORA);
		var a = new Evolucao.Ativo(i, List.of(inicial, resgate), semCdi(), null);

		var r = Evolucao.calcular(Evolucao.Periodo.MES, HOJE, List.of(a), cdiAteOntem());

		assertThat(ponto(r, 15)).isGreaterThan(n(10000));
		assertThat(ponto(r, 5)).isEqualByComparingTo("0.00");
	}

	@Test
	void tesouroPeloPrecoDoDia() {
		var i = new Investimento(usuario, Investimento.Tipo.TESOURO, "Tesouro IPCA+ 2035", "Tesouro IPCA+ 2035", null,
				true, AGORA);
		var inicial = new Movimentacao(i.getId(), usuario, Movimentacao.Tipo.INICIAL, dia(20), n(1), null, n(3000),
				false, AGORA);
		var a = new Evolucao.Ativo(i, List.of(inicial), precos(10, 3100), null);

		var r = Evolucao.calcular(Evolucao.Periodo.MES, HOJE, List.of(a), semCdi());

		assertThat(ponto(r, 15)).isEqualByComparingTo("3000.00");
		assertThat(ponto(r, 10)).isEqualByComparingTo("3100.00");
	}

	@Test
	void cdiDoPeriodo() {
		var i = acao("PETR4");
		var a = new Evolucao.Ativo(i, List.of(mov(i, Movimentacao.Tipo.INICIAL, 90, 10, 30)), precos(90, 30), n(30));
		var cdi = cdiAteOntem();

		var mes = Evolucao.calcular(Evolucao.Periodo.MES, HOJE, List.of(a), cdi);

		var ini = HOJE.minusMonths(1);
		var dias = cdi.subMap(ini, false, HOJE, true).size();
		var esperado = n("1.0004").pow(dias, MathContext.DECIMAL128)
			.subtract(BigDecimal.ONE)
			.multiply(n(100))
			.setScale(2, RoundingMode.HALF_UP);
		assertThat(mes.cdiPercentual()).isEqualByComparingTo(esperado);
		assertThat(mes.cdiAte()).isEqualTo(dia(1));

		var hoje = Evolucao.calcular(Evolucao.Periodo.DIA, HOJE, List.of(a), cdi);

		assertThat(hoje.cdiAte()).isEqualTo(dia(1));
		assertThat(hoje.cdiPercentual()).isEqualByComparingTo("0.04");
	}

	@Test
	void rendaFixaSemCdiFicaForaComoNoPatrimonio() {
		var i = Investimento.rendaFixa(usuario, Investimento.Produto.CDB, "Banco X", Investimento.Indexador.CDI,
				n(110), HOJE.plusYears(2), AGORA);
		var hoje = new Movimentacao(i.getId(), usuario, Movimentacao.Tipo.INICIAL, HOJE, null, null, n(10000), false,
				AGORA);
		var cdb = new Evolucao.Ativo(i, List.of(hoje), semCdi(), null);

		var r = Evolucao.calcular(Evolucao.Periodo.MES, HOJE, List.of(petrComAporte(), cdb), cdiAteOntem());

		assertThat(r.valorFinal()).isEqualByComparingTo("720.00");
		assertThat(r.aportes()).isEqualByComparingTo("630.00");
	}

	@Test
	void cdiDesdeOCadastro() {
		var r = Evolucao.calcular(Evolucao.Periodo.DOZE_MESES, HOJE, List.of(petrComAporte()), cdiAteOntem());

		var dias = cdiAteOntem().subMap(dia(20), false, HOJE, true).size();
		var esperado = n("1.0004").pow(dias, MathContext.DECIMAL128)
			.subtract(BigDecimal.ONE)
			.multiply(n(100))
			.setScale(2, RoundingMode.HALF_UP);
		assertThat(r.desdeCadastro()).isTrue();
		assertThat(r.cdiPercentual()).isEqualByComparingTo(esperado);
	}

	@Test
	void carteiraVazia() {
		var r = Evolucao.calcular(Evolucao.Periodo.MES, HOJE, new ArrayList<>(), cdiAteOntem());

		assertThat(r.pontos()).isEmpty();
		assertThat(r.valorFinal()).isEqualByComparingTo("0.00");
		assertThat(r.aportes()).isEqualByComparingTo("0.00");
		assertThat(r.ganho()).isEqualByComparingTo("0.00");
		assertThat(r.ganhoPercentual()).isNull();
	}

}
