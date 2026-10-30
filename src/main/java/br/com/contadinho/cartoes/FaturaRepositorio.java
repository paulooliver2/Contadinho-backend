package br.com.contadinho.cartoes;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FaturaRepositorio extends JpaRepository<Fatura, UUID> {

	/** A fatura que vence entre as duas datas, isto é, num dado mês (RN-58). */
	Optional<Fatura> findFirstByCartaoIdAndVencimentoBetweenOrderByVencimento(UUID cartaoId, LocalDate de, LocalDate ate);

}
