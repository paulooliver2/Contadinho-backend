package br.com.contadinho.cartoes;

import java.util.List;
import java.util.UUID;

/**
 * Evento: a importação da fatura gravou compras novas (HU-06). Publicado dentro da transação, para o módulo Gastos
 * ligar as contas fixas pagas no cartão aos lançamentos delas (HU-14, RN-23).
 */
public record ComprasImportadas(UUID usuarioId, UUID cartaoId, List<UUID> lancamentoIds) {
}
