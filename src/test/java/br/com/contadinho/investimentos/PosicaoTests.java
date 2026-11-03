package br.com.contadinho.investimentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.investimentos.domain.Investimento;
import br.com.contadinho.investimentos.domain.Movimentacao;
import br.com.contadinho.investimentos.domain.Posicao;
import org.junit.jupiter.api.Test;

/** HU-30: a posição sai das movimentações (RNF-05); o aporte muda o preço médio e o resgate não (RN-48). */
class PosicaoTests {

	private static final LocalDate DIA = LocalDate.of(2026, 11, 3);

	private final UUID investimento = UUID.randomUUID();

	private final List<Movimentacao> movs = new ArrayList<>();

	private Instant relogio = Instant.parse("2026-11-03T12:00:00Z");

	private static BigDecimal n(String valor) {
		return new BigDecimal(valor);
	}

	private Movimentacao mov(Movimentacao.Tipo tipo, LocalDate data, String quantidade, String preco, String valor,
			boolean total) {
		relogio = relogio.plusSeconds(1);
		var m = new Movimentacao(investimento, UUID.randomUUID(), tipo, data, quantidade == null ? null : n(quantidade),
				preco == null ? null : n(preco), valor == null ? null : n(valor), total, relogio);
		movs.add(m);
		return m;
	}

	private Movimentacao compra(Movimentacao.Tipo tipo, String quantidade, String preco) {
		return mov(tipo, DIA, quantidade, preco, null, false);
	}

	private Movimentacao venda(String quantidade) {
		return mov(Movimentacao.Tipo.RESGATE, DIA, quantidade, null, null, false);
	}

	private Posicao acao() {
		return Posicao.de(Investimento.Tipo.ACAO, movs);
	}

	@Test
	void aporteRecalculaPrecoMedioEResgateNao() {
		compra(Movimentacao.Tipo.INICIAL, "100", "10");
		assertThat(acao()).satisfies(p -> {
			assertThat(p.quantidade()).isEqualByComparingTo("100");
			assertThat(p.precoMedio()).isEqualByComparingTo("10");
			assertThat(p.aplicado()).isEqualByComparingTo("1000.00");
			assertThat(p.encerrada()).isFalse();
		});

		compra(Movimentacao.Tipo.APORTE, "100", "20");
		assertThat(acao().precoMedio()).isEqualByComparingTo("15");

		venda("50");
		assertThat(acao()).satisfies(p -> {
			assertThat(p.quantidade()).isEqualByComparingTo("150");
			assertThat(p.precoMedio()).isEqualByComparingTo("15");
			assertThat(p.aplicado()).isEqualByComparingTo("2250.00");
		});

		venda("150");
		assertThat(acao().encerrada()).isTrue();
		assertThat(acao().aplicado()).isEqualByComparingTo("0");
	}

	@Test
	void depoisDeZerarOAporteRecomecaOPrecoMedio() {
		compra(Movimentacao.Tipo.INICIAL, "10", "10");
		venda("10");
		compra(Movimentacao.Tipo.APORTE, "10", "30");

		assertThat(acao().precoMedio()).isEqualByComparingTo("30");
		assertThat(acao().encerrada()).isFalse();
	}

	@Test
	void mesmoDiaSegueAOrdemDeLancamento() {
		compra(Movimentacao.Tipo.INICIAL, "10", "10");
		venda("10");
		// Lançado depois, no mesmo dia: o resgate zerou antes, então o aporte recomeça o preço médio.
		compra(Movimentacao.Tipo.APORTE, "10", "20");

		assertThat(acao().precoMedio()).isEqualByComparingTo("20");
	}

	@Test
	void aplicadoArredondaACentavos() {
		compra(Movimentacao.Tipo.INICIAL, "3", "10.333");

		assertThat(acao().aplicado()).isEqualTo(n("31.00"));
	}

	@Test
	void criptoDePrecoMinusculoNaoViraZero() {
		compra(Movimentacao.Tipo.INICIAL, "1000000", "0.00001234");

		var p = Posicao.de(Investimento.Tipo.CRIPTO, movs);
		assertThat(p.precoMedio()).isEqualByComparingTo("0.00001234");
		assertThat(p.aplicado()).isEqualTo(n("12.34"));
	}

	@Test
	void rendaFixaAbateResgatesEEncerraComTotal() {
		mov(Movimentacao.Tipo.INICIAL, DIA, null, null, "1000", false);
		mov(Movimentacao.Tipo.RESGATE, DIA, null, null, "300", false);
		assertThat(Posicao.de(Investimento.Tipo.RENDA_FIXA, movs).aplicado()).isEqualByComparingTo("700");

		mov(Movimentacao.Tipo.RESGATE, DIA, null, null, "2000", false);
		assertThat(Posicao.de(Investimento.Tipo.RENDA_FIXA, movs).aplicado()).isEqualByComparingTo("0");
		assertThat(Posicao.de(Investimento.Tipo.RENDA_FIXA, movs).encerrada()).isFalse();

		mov(Movimentacao.Tipo.RESGATE, DIA, null, null, "10", true);
		assertThat(Posicao.de(Investimento.Tipo.RENDA_FIXA, movs).encerrada()).isTrue();
	}

	@Test
	void problemaQuandoAlgumPassoFicaNegativo() {
		compra(Movimentacao.Tipo.INICIAL, "10", "10");
		var aporte = compra(Movimentacao.Tipo.APORTE, "10", "10");
		venda("15");
		assertThat(Posicao.problema(Investimento.Tipo.ACAO, movs)).isEmpty();

		movs.remove(aporte);

		assertThat(Posicao.problema(Investimento.Tipo.ACAO, movs)).isPresent();
	}

	@Test
	void problemaQuandoMexeDepoisDeEncerradaARendaFixa() {
		mov(Movimentacao.Tipo.INICIAL, DIA, null, null, "1000", false);
		mov(Movimentacao.Tipo.RESGATE, DIA, null, null, "1100", true);
		assertThat(Posicao.problema(Investimento.Tipo.RENDA_FIXA, movs)).isEmpty();

		mov(Movimentacao.Tipo.RESGATE, DIA.plusDays(1), null, null, "10", false);

		assertThat(Posicao.problema(Investimento.Tipo.RENDA_FIXA, movs)).isPresent();
	}

}
