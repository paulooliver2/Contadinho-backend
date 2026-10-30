package br.com.contadinho.cartoes;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
interface CartaoRepositorio extends JpaRepository<Cartao, UUID> {

	/** Encerrados somem da tela Cartões e das listas de escolha (RN-55). */
	List<Cartao> findByUsuarioIdAndEncerradoEmIsNullOrderByCriadoEm(UUID usuarioId);

}
