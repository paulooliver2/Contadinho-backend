package br.com.contadinho.gastos.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.gastos.domain.ContaFixa;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
public interface ContaFixaRepositorio extends JpaRepository<ContaFixa, UUID> {

	List<ContaFixa> findByUsuarioId(UUID usuarioId);

	List<ContaFixa> findByUsuarioIdAndCartaoId(UUID usuarioId, UUID cartaoId);

	Optional<ContaFixa> findByIdAndUsuarioId(UUID id, UUID usuarioId);

}
