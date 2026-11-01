package br.com.contadinho.gastos.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.gastos.domain.ContaFixaValor;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaFixaValorRepositorio extends JpaRepository<ContaFixaValor, UUID> {

	List<ContaFixaValor> findByContaFixaIdIn(Collection<UUID> contaFixaIds);

}
