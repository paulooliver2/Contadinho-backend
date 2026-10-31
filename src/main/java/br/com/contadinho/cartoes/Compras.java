package br.com.contadinho.cartoes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * O que os outros módulos podem ler das compras das faturas, como o histórico da pessoa em A receber (HU-18).
 */
public interface Compras {

	/** As compras do dono com esses ids; as de outro dono não vêm (RNF-01). */
	List<Compra> buscar(UUID usuarioId, Collection<UUID> lancamentoIds);

	/**
	 * @param cartao apelido e final, como "Itaú •• 3310"
	 * @param fatura mês de vencimento da fatura (RN-58)
	 */
	record Compra(UUID id, LocalDate data, String descricao, BigDecimal valor, Integer parcelaAtual,
			Integer parcelaTotal, String cartao, YearMonth fatura) {
	}

}
