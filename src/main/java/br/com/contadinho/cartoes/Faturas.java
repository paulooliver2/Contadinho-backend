package br.com.contadinho.cartoes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** O que os outros módulos leem das faturas do dono, como o resumo do mês (HU-23). */
public interface Faturas {

	/** As faturas gravadas que vencem no mês (RN-37), de todos os cartões do dono, encerrados inclusive. */
	List<FaturaDoMes> queVencemEm(UUID usuarioId, YearMonth mes);

	/**
	 * @param cartao apelido e final, como "Inter •• 9453"
	 * @param minhaParte as compras sem pessoa, isto é, do dono (RN-15); estornos abatem
	 */
	record FaturaDoMes(UUID cartaoId, String cartao, LocalDate vencimento, BigDecimal total, BigDecimal minhaParte) {
	}

}
