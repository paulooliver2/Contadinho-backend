package br.com.contadinho.gastos;

import java.math.BigDecimal;
import java.time.YearMonth;
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

}
