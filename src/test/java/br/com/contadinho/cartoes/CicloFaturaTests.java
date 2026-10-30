package br.com.contadinho.cartoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * RN-58 e RN-08, sem banco.
 */
class CicloFaturaTests {

	private static Cartao cartao(int diaFechamento, int diaVencimento) {
		return new Cartao(UUID.randomUUID(), "Teste", Banco.ITAU, "1234", diaFechamento, diaVencimento, Instant.now());
	}

	@Test
	void fechaNoMesmoMesQuandoOFechamentoVemAntes() {
		var ciclo = cartao(1, 10).ciclo(YearMonth.of(2026, 11));

		assertThat(ciclo.fechamento()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(ciclo.vencimento()).isEqualTo(LocalDate.of(2026, 11, 10));
	}

	@Test
	void fechaNoMesAnteriorQuandoOFechamentoVemDepois() {
		var ciclo = cartao(28, 8).ciclo(YearMonth.of(2026, 11));

		assertThat(ciclo.fechamento()).isEqualTo(LocalDate.of(2026, 10, 28));
		assertThat(ciclo.vencimento()).isEqualTo(LocalDate.of(2026, 11, 8));
	}

	@Test
	void emMesCurtoValeOUltimoDia() {
		var ciclo = cartao(31, 10).ciclo(YearMonth.of(2026, 3));

		assertThat(ciclo.fechamento()).isEqualTo(LocalDate.of(2026, 2, 28));
	}

	@Test
	void faturaAbertaEAPrimeiraQueAindaNaoFechou() {
		var cartao = cartao(28, 8);

		assertThat(cartao.mesAberto(LocalDate.of(2026, 10, 27))).isEqualTo(YearMonth.of(2026, 11));
		assertThat(cartao.mesAberto(LocalDate.of(2026, 10, 28))).isEqualTo(YearMonth.of(2026, 11));
		assertThat(cartao.mesAberto(LocalDate.of(2026, 10, 29))).isEqualTo(YearMonth.of(2026, 12));
		assertThat(cartao(1, 10).mesAberto(LocalDate.of(2026, 11, 2))).isEqualTo(YearMonth.of(2026, 12));
	}

}
