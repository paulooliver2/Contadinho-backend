package br.com.contadinho.cartoes.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.cartoes.domain.Importacao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacaoRepositorio extends JpaRepository<Importacao, UUID> {

	Optional<Importacao> findByIdAndUsuarioId(UUID id, UUID usuarioId);

	/** HU-44: as importações da fatura, da primeira para a última. */
	List<Importacao> findByFaturaIdOrderByImportadaEm(UUID faturaId);

	boolean existsByFaturaId(UUID faturaId);

}
