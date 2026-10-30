package br.com.contadinho.cartoes;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
interface LancamentoRepositorio extends JpaRepository<Lancamento, UUID> {

	List<Lancamento> findByFaturaIdOrderByDataDescDescricao(UUID faturaId);

	Optional<Lancamento> findByIdAndUsuarioId(UUID id, UUID usuarioId);

	/** RN-11: os que já vieram de um arquivo deste cartão. */
	List<Lancamento> findByCartaoIdAndIdExternoIn(UUID cartaoId, Collection<String> idsExternos);

	/** RN-59: candidatas a casar com as compras do arquivo. */
	List<Lancamento> findByFaturaIdAndOrigemOrderByDataAscDescricao(UUID faturaId, Lancamento.Origem origem);

	/** RN-17: candidatas a próximas parcelas da mesma compra. */
	List<Lancamento> findByCartaoIdAndParcelaTotalAndParcelaAtualGreaterThan(UUID cartaoId, Short parcelaTotal,
			Short parcelaAtual);

}
