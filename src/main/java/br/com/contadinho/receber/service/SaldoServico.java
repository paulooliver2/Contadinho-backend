package br.com.contadinho.receber.service;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.contadinho.pessoas.SaldoDaPessoa;
import br.com.contadinho.receber.repository.PagamentoRepositorio;
import br.com.contadinho.receber.repository.RecebivelRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SaldoDaPessoa} para o módulo de pessoas (RN-36). Fica separado do {@link ReceberServico}, que usa as
 * pessoas, para não fechar um ciclo entre os dois.
 */
@Service
@Transactional(readOnly = true)
public class SaldoServico implements SaldoDaPessoa {

	private final RecebivelRepositorio recebiveis;

	private final PagamentoRepositorio pagamentos;

	public SaldoServico(RecebivelRepositorio recebiveis, PagamentoRepositorio pagamentos) {
		this.recebiveis = recebiveis;
		this.pagamentos = pagamentos;
	}

	@Override
	public BigDecimal saldo(UUID usuarioId, UUID pessoaId) {
		return ReceberServico.situacao(pessoaId, recebiveis.findByUsuarioIdAndPessoaId(usuarioId, pessoaId),
				pagamentos.findByUsuarioIdAndPessoaIdAndDesfeitoEmIsNullOrderByRecebidoEmDescCriadoEmDesc(usuarioId,
						pessoaId))
			.saldo();
	}

}
