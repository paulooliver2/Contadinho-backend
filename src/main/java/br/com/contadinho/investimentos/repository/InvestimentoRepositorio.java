package br.com.contadinho.investimentos.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.investimentos.domain.Investimento;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestimentoRepositorio extends JpaRepository<Investimento, UUID> {

	List<Investimento> findByUsuarioIdOrderByCriadoEm(UUID usuarioId);

	Optional<Investimento> findByIdAndUsuarioId(UUID id, UUID usuarioId);

	boolean existsByUsuarioIdAndTipoAndCodigo(UUID usuarioId, Investimento.Tipo tipo, String codigo);

}
