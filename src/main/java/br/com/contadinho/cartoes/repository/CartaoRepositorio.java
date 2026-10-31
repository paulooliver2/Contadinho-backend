package br.com.contadinho.cartoes.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.cartoes.domain.Cartao;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
public interface CartaoRepositorio extends JpaRepository<Cartao, UUID> {

	/** Encerrados somem da tela Cartões e das listas de escolha (RN-55). */
	List<Cartao> findByUsuarioIdAndEncerradoEmIsNullOrderByCriadoEm(UUID usuarioId);

	Optional<Cartao> findByIdAndUsuarioIdAndEncerradoEmIsNull(UUID id, UUID usuarioId);

}
