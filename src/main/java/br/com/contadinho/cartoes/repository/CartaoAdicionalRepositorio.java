package br.com.contadinho.cartoes.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.cartoes.domain.CartaoAdicional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
public interface CartaoAdicionalRepositorio extends JpaRepository<CartaoAdicional, UUID> {

	List<CartaoAdicional> findByUsuarioIdAndCartaoIdOrderByUltimos4(UUID usuarioId, UUID cartaoId);

	Optional<CartaoAdicional> findByIdAndUsuarioIdAndCartaoId(UUID id, UUID usuarioId, UUID cartaoId);

	boolean existsByCartaoIdAndUltimos4(UUID cartaoId, String ultimos4);

}
