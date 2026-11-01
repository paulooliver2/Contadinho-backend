package br.com.contadinho.gastos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * O que os outros módulos leem das despesas fora do cartão, como o resumo do mês (HU-23). O que foi pago no cartão não
 * entra aqui: vale o lançamento da fatura (RN-23, RN-26).
 */
public interface Despesas {

	/** Cria as ocorrências de conta fixa que faltam no mês, como abrir a aba Gastos. */
	DespesasDoMes doMes(UUID usuarioId, YearMonth mes);

	/**
	 * @param contasPagas contas fixas pagas, pelo valor pago (RN-25)
	 * @param contasAPagar contas fixas a pagar, pelo valor previsto
	 * @param avulsos gastos avulsos, todos já pagos
	 */
	record DespesasDoMes(BigDecimal contasPagas, BigDecimal contasAPagar, BigDecimal avulsos) {
	}

	/**
	 * HU-24: as contas fixas fora do cartão ainda a pagar, deste mês e do próximo, pelo vencimento; as deste mês que
	 * já venceram vêm primeiro. Cria as ocorrências que faltam nos dois meses.
	 */
	ContasAVencer aVencer(UUID usuarioId, int quantas);

	/**
	 * @param fixosPorMes o valor do cadastro de todas as contas fixas ativas, no cartão inclusive
	 * @param proximas no máximo {@code quantas}
	 */
	record ContasAVencer(BigDecimal fixosPorMes, List<ContaAVencer> proximas) {
	}

	/** {@code valor} é o previsto (RN-25). */
	record ContaAVencer(UUID ocorrenciaId, String descricao, LocalDate vencimento, BigDecimal valor) {
	}

	/**
	 * HU-26: as contas fixas fora do cartão ainda a pagar que vencem de hoje até {@code ate}, pelo vencimento. Cria as
	 * ocorrências que faltam nesses meses.
	 */
	List<ContaParaAvisar> aPagarAte(UUID usuarioId, LocalDate ate);

	/** {@code debitoAutomatico}: o banco paga sozinho no vencimento, então não há aviso no dia. */
	record ContaParaAvisar(UUID ocorrenciaId, String descricao, LocalDate vencimento, BigDecimal valor,
			boolean debitoAutomatico) {
	}

}
