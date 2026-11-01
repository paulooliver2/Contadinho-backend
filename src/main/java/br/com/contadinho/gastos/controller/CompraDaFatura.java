package br.com.contadinho.gastos.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import br.com.contadinho.cartoes.Compras;
import jakarta.validation.constraints.NotNull;

/** Uma compra da fatura que paga, ou pode pagar, uma conta fixa (HU-14) ou um gasto no cartão (RN-26). */
public record CompraDaFatura(
		@NotNull UUID id,
		@NotNull LocalDate data,
		@NotNull String descricao,
		@NotNull BigDecimal valor,
		Integer parcelaAtual,
		Integer parcelaTotal,
		/** Apelido e final, como "Inter •• 9453". */
		@NotNull String cartao,
		/** Mês de vencimento da fatura. */
		@NotNull YearMonth fatura) {

	public static CompraDaFatura de(Compras.Compra c) {
		return new CompraDaFatura(c.id(), c.data(), c.descricao(), c.valor(), c.parcelaAtual(), c.parcelaTotal(),
				c.cartao(), c.fatura());
	}

}
