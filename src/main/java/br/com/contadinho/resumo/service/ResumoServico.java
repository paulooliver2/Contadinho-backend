package br.com.contadinho.resumo.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.cartoes.Faturas;
import br.com.contadinho.gastos.Despesas;
import br.com.contadinho.receitas.Receitas;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-23: receitas, despesas e saldo do mês, sempre somados a partir dos lançamentos (RN-39). Despesas = minha parte
 * das faturas que vencem no mês + contas fixas fora do cartão + gastos avulsos fora do cartão (RN-38).
 */
@Service
public class ResumoServico {

	private final Receitas receitas;

	private final Despesas despesas;

	private final Faturas faturas;

	private final Clock clock;

	public ResumoServico(Receitas receitas, Despesas despesas, Faturas faturas, Clock clock) {
		this.receitas = receitas;
		this.despesas = despesas;
		this.faturas = faturas;
		this.clock = clock;
	}

	/**
	 * O app não sabe quando a fatura foi paga: a que já venceu conta como paga; a que vence hoje ou depois, como a
	 * pagar.
	 */
	public record Resumo(Receitas.ReceitasDoMes receitas, Despesas.DespesasDoMes fora, List<Faturas.FaturaDoMes> faturas,
			LocalDate hoje) {

		public BigDecimal minhaParteDasFaturas() {
			return soma(faturas);
		}

		public BigDecimal totalDespesas() {
			return minhaParteDasFaturas().add(fora.contasPagas()).add(fora.contasAPagar()).add(fora.avulsos());
		}

		public BigDecimal aPagar() {
			return fora.contasAPagar().add(soma(faturas.stream().filter(f -> !f.vencimento().isBefore(hoje)).toList()));
		}

		public BigDecimal pago() {
			return totalDespesas().subtract(aPagar());
		}

		public BigDecimal saldo() {
			return receitas.total().subtract(totalDespesas());
		}

		private static BigDecimal soma(List<Faturas.FaturaDoMes> lista) {
			return lista.stream().map(Faturas.FaturaDoMes::minhaParte).reduce(BigDecimal.ZERO, BigDecimal::add);
		}
	}

	/** Cria as ocorrências que faltam no mês, como abrir as telas de Receitas e Gastos. */
	@Transactional
	public Resumo doMes(UUID usuarioId, YearMonth mes) {
		return new Resumo(receitas.doMes(usuarioId, mes), despesas.doMes(usuarioId, mes),
				faturas.queVencemEm(usuarioId, mes), LocalDate.now(clock));
	}

}
