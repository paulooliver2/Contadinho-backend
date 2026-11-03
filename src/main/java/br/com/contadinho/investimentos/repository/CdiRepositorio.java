package br.com.contadinho.investimentos.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import br.com.contadinho.investimentos.domain.CdiDiario;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CdiRepositorio extends JpaRepository<CdiDiario, LocalDate> {

	List<CdiDiario> findByDataBetweenOrderByData(LocalDate de, LocalDate ate);

	Optional<CdiDiario> findTopByOrderByDataDesc();

}
