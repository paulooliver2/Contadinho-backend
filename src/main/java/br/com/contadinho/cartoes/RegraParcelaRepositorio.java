package br.com.contadinho.cartoes;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** As regras são do cartão, que já é do dono (RNF-01). */
interface RegraParcelaRepositorio extends JpaRepository<RegraParcela, UUID> {

	/** Candidatas; quem decide se é a mesma compra é {@link RegraParcela#mesmaCompra}. */
	List<RegraParcela> findByCartaoIdAndParcelaTotal(UUID cartaoId, Short parcelaTotal);

}
