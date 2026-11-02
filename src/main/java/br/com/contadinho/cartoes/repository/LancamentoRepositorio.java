package br.com.contadinho.cartoes.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.cartoes.domain.Lancamento;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
public interface LancamentoRepositorio extends JpaRepository<Lancamento, UUID> {

	List<Lancamento> findByFaturaIdOrderByDataDescDescricao(UUID faturaId);

	/** HU-44: o que uma importação gravou ou casou. */
	List<Lancamento> findByImportacaoId(UUID importacaoId);

	boolean existsByFaturaId(UUID faturaId);

	List<Lancamento> findByFaturaIdIn(Collection<UUID> faturaIds);

	Optional<Lancamento> findByIdAndUsuarioId(UUID id, UUID usuarioId);

	List<Lancamento> findByUsuarioIdAndIdIn(UUID usuarioId, Collection<UUID> ids);

	List<Lancamento> findByUsuarioIdAndCartaoIdAndDataBetweenAndValorGreaterThan(UUID usuarioId, UUID cartaoId,
			LocalDate de, LocalDate ate, BigDecimal valor);

	/** RN-11: os que já vieram de um arquivo deste cartão. */
	List<Lancamento> findByCartaoIdAndIdExternoIn(UUID cartaoId, Collection<String> idsExternos);

	/** RN-59: candidatas a casar com as compras do arquivo. */
	List<Lancamento> findByFaturaIdAndOrigemOrderByDataAscDescricao(UUID faturaId, Lancamento.Origem origem);

	/** RN-17: candidatas a próximas parcelas da mesma compra. */
	List<Lancamento> findByCartaoIdAndParcelaTotalAndParcelaAtualGreaterThan(UUID cartaoId, Short parcelaTotal,
			Short parcelaAtual);

}
