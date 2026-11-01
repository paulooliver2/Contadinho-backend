package br.com.contadinho.pessoas;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * O que os outros módulos podem perguntar sobre as pessoas do dono.
 */
public interface Pessoas {

	/** A pessoa é do dono e não está arquivada, então pode ser escolhida numa lista (RN-36). */
	boolean podeEscolher(UUID usuarioId, UUID pessoaId);

	/** A pessoa é do dono, arquivada ou não (RNF-01). */
	boolean existe(UUID usuarioId, UUID pessoaId);

	/** Como cada pessoa do dono aparece em textos curtos, como um aviso: o apelido, ou o nome. */
	Map<UUID, String> rotulos(UUID usuarioId, Collection<UUID> pessoaIds);

}
