package br.com.contadinho.cartoes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
interface LancamentoRepositorio extends JpaRepository<Lancamento, UUID> {

	List<Lancamento> findByFaturaIdOrderByDataDescDescricao(UUID faturaId);

	Optional<Lancamento> findByIdAndUsuarioId(UUID id, UUID usuarioId);

}
