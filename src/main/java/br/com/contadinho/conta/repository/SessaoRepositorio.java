package br.com.contadinho.conta.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.conta.domain.Sessao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface SessaoRepositorio extends JpaRepository<Sessao, UUID> {

	Optional<Sessao> findByTokenHash(String tokenHash);

	/** Devolve 0 se outra requisição já revogou a sessão: o mesmo refresh token não renova duas vezes. */
	@Transactional
	@Modifying
	@Query("update Sessao s set s.revogadaEm = :agora where s.id = :id and s.revogadaEm is null")
	int revogar(UUID id, Instant agora);

	/** RN-04: redefinir a senha encerra todas as sessões. */
	@Transactional
	@Modifying
	@Query("update Sessao s set s.revogadaEm = :agora where s.usuarioId = :usuarioId and s.revogadaEm is null")
	int revogarTodas(UUID usuarioId, Instant agora);

}
