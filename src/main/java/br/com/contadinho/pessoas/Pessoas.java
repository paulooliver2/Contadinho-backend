package br.com.contadinho.pessoas;

import java.util.UUID;

/**
 * O que os outros módulos podem perguntar sobre as pessoas do dono.
 */
public interface Pessoas {

	/** A pessoa é do dono e não está arquivada, então pode ser escolhida numa lista (RN-36). */
	boolean podeEscolher(UUID usuarioId, UUID pessoaId);

}
