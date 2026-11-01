package br.com.contadinho.gastos.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.gastos.domain.Gasto;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
public interface GastoRepositorio extends JpaRepository<Gasto, UUID> {

	Optional<Gasto> findByIdAndUsuarioId(UUID id, UUID usuarioId);

	List<Gasto> findByUsuarioIdAndDataBetween(UUID usuarioId, LocalDate de, LocalDate ate);

	/** RN-26: os do cartão que ainda aguardam a fatura. */
	List<Gasto> findByUsuarioIdAndCartaoIdAndLancamentoIdIsNullAndDataBetween(UUID usuarioId, UUID cartaoId,
			LocalDate de, LocalDate ate);

	List<Gasto> findByUsuarioIdAndLancamentoIdIn(UUID usuarioId, Collection<UUID> lancamentoIds);

}
