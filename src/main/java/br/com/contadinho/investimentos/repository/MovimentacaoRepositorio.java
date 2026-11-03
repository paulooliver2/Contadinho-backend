package br.com.contadinho.investimentos.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.investimentos.domain.Movimentacao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimentacaoRepositorio extends JpaRepository<Movimentacao, UUID> {

	List<Movimentacao> findByInvestimentoId(UUID investimentoId);

	List<Movimentacao> findByInvestimentoIdIn(Collection<UUID> investimentoIds);

}
