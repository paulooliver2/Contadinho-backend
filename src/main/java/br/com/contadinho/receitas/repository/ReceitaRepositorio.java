package br.com.contadinho.receitas.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.receitas.domain.Receita;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
public interface ReceitaRepositorio extends JpaRepository<Receita, UUID> {

	List<Receita> findByUsuarioId(UUID usuarioId);

	Optional<Receita> findByIdAndUsuarioId(UUID id, UUID usuarioId);

}
