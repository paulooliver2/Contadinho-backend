package br.com.contadinho.receitas;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/** O que os outros módulos leem das receitas, como o resumo do mês (HU-23). */
public interface Receitas {

	/** Cria as ocorrências que faltam no mês, como abrir a tela Receitas. */
	ReceitasDoMes doMes(UUID usuarioId, YearMonth mes);

	/**
	 * @param recebido o que o dono confirmou, pelo valor que caiu
	 * @param previsto o que ainda não confirmou, pelo valor previsto
	 */
	record ReceitasDoMes(BigDecimal recebido, BigDecimal previsto) {

		public BigDecimal total() {
			return recebido.add(previsto);
		}
	}

}
