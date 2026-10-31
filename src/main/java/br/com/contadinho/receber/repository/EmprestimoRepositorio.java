package br.com.contadinho.receber.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.receber.domain.Emprestimo;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
public interface EmprestimoRepositorio extends JpaRepository<Emprestimo, UUID> {

	Optional<Emprestimo> findByIdAndUsuarioId(UUID id, UUID usuarioId);

	List<Emprestimo> findByUsuarioIdAndPessoaIdOrderByCriadoEmDesc(UUID usuarioId, UUID pessoaId);

}
