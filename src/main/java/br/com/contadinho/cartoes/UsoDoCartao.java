package br.com.contadinho.cartoes;

import java.util.UUID;

/**
 * Implementado pelos módulos que guardam um cartão como forma de pagamento (contas fixas, gastos). Cartão em uso não
 * se exclui, só se encerra (HU-45, RN-55).
 */
public interface UsoDoCartao {

	boolean usa(UUID usuarioId, UUID cartaoId);

}
