package br.com.contadinho.receber.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.receber.domain.Pagamento;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). Só os não desfeitos contam no saldo (HU-19.3). */
public interface PagamentoRepositorio extends JpaRepository<Pagamento, UUID> {

	Optional<Pagamento> findByIdAndUsuarioId(UUID id, UUID usuarioId);

	List<Pagamento> findByUsuarioIdAndDesfeitoEmIsNull(UUID usuarioId);

	List<Pagamento> findByUsuarioIdAndPessoaIdAndDesfeitoEmIsNullOrderByRecebidoEmDescCriadoEmDesc(UUID usuarioId,
			UUID pessoaId);

}
