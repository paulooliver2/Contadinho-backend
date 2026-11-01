package br.com.contadinho.receber.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.receber.domain.Cobranca;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Toda consulta recebe o usuário dono (RNF-01). */
public interface CobrancaRepositorio extends JpaRepository<Cobranca, UUID> {

	interface Ultima {

		UUID getPessoaId();

		Instant getEnviadaEm();

	}

	/** A cobrança mais recente de cada pessoa do dono. */
	@Query("select c.pessoaId as pessoaId, max(c.enviadaEm) as enviadaEm from Cobranca c "
			+ "where c.usuarioId = :usuarioId group by c.pessoaId")
	List<Ultima> ultimas(UUID usuarioId);

	Optional<Cobranca> findFirstByUsuarioIdAndPessoaIdOrderByEnviadaEmDesc(UUID usuarioId, UUID pessoaId);

}
