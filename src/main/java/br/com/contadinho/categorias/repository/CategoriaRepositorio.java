package br.com.contadinho.categorias.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.categorias.domain.Categoria;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
public interface CategoriaRepositorio extends JpaRepository<Categoria, UUID> {

	List<Categoria> findByUsuarioIdOrderByNome(UUID usuarioId);

	Optional<Categoria> findByIdAndUsuarioId(UUID id, UUID usuarioId);

	boolean existsByUsuarioId(UUID usuarioId);

}
