package br.com.contadinho.conta;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface TokenRecuperacaoRepositorio extends JpaRepository<TokenRecuperacao, UUID> {

	Optional<TokenRecuperacao> findByTokenHash(String tokenHash);

	/** Algum link deste usuário vence depois de {@code instante}? Serve para espaçar os envios. */
	boolean existsByUsuarioIdAndExpiraEmAfter(UUID usuarioId, Instant instante);

	/** Devolve 0 se o link já foi usado: dois cliques simultâneos não trocam a senha duas vezes. */
	@Transactional
	@Modifying
	@Query("update TokenRecuperacao t set t.usadoEm = :agora where t.id = :id and t.usadoEm is null")
	int usar(UUID id, Instant agora);

	/** Depois da troca, os outros links pedidos pelo mesmo usuário deixam de valer. */
	@Transactional
	@Modifying
	@Query("update TokenRecuperacao t set t.usadoEm = :agora where t.usuarioId = :usuarioId and t.usadoEm is null")
	int invalidarTodos(UUID usuarioId, Instant agora);

}
