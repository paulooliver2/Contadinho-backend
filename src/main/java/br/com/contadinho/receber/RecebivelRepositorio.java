package br.com.contadinho.receber;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
interface RecebivelRepositorio extends JpaRepository<Recebivel, UUID> {

	Optional<Recebivel> findByLancamentoId(UUID lancamentoId);

	List<Recebivel> findByUsuarioId(UUID usuarioId);

	List<Recebivel> findByUsuarioIdAndPessoaId(UUID usuarioId, UUID pessoaId);

}
