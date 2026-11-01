package br.com.contadinho.cartoes;

import java.util.UUID;

/** O que os outros módulos perguntam sobre os cartões do dono. */
public interface Cartoes {

	/** Do dono e não encerrado: pode ser a forma de pagamento de uma conta ou gasto (RN-22). */
	boolean ativo(UUID usuarioId, UUID cartaoId);

}
