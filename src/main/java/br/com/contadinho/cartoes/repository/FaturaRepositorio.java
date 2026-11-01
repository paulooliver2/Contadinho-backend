package br.com.contadinho.cartoes.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.cartoes.domain.Fatura;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FaturaRepositorio extends JpaRepository<Fatura, UUID> {

	/** A fatura que vence entre as duas datas, isto é, num dado mês (RN-58). */
	Optional<Fatura> findFirstByCartaoIdAndVencimentoBetweenOrderByVencimento(UUID cartaoId, LocalDate de, LocalDate ate);

	List<Fatura> findByUsuarioIdAndVencimentoBetween(UUID usuarioId, LocalDate de, LocalDate ate);

}
