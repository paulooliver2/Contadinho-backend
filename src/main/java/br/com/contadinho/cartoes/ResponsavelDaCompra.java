package br.com.contadinho.cartoes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento: o responsável ou o valor de uma compra da fatura mudou (HU-08, RN-16). Publicado dentro da transação,
 * para o módulo A receber manter o recebível da compra junto.
 *
 * @param pessoaId null quando a compra é do dono e não gera recebível
 * @param valor negativo quando é estorno
 * @param vencimento vencimento da fatura em que a compra está
 */
public record ResponsavelDaCompra(UUID usuarioId, UUID lancamentoId, UUID pessoaId, BigDecimal valor,
		LocalDate vencimento) {
}
