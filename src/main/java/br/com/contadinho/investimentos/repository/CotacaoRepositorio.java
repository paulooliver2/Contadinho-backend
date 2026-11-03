package br.com.contadinho.investimentos.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.investimentos.domain.Cotacao;
import br.com.contadinho.investimentos.domain.Investimento;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CotacaoRepositorio extends JpaRepository<Cotacao, UUID> {

	List<Cotacao> findByTipoAndCodigoIn(Investimento.Tipo tipo, Collection<String> codigos);

}
