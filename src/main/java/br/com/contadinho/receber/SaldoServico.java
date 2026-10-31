package br.com.contadinho.receber;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.contadinho.pessoas.SaldoDaPessoa;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SaldoDaPessoa} para o módulo de pessoas (RN-36). Fica separado do {@link ReceberServico}, que usa as
 * pessoas, para não fechar um ciclo entre os dois.
 */
@Service
@Transactional(readOnly = true)
class SaldoServico implements SaldoDaPessoa {

	private final RecebivelRepositorio recebiveis;

	SaldoServico(RecebivelRepositorio recebiveis) {
		this.recebiveis = recebiveis;
	}

	@Override
	public BigDecimal saldo(UUID usuarioId, UUID pessoaId) {
		return ReceberServico.situacao(pessoaId, recebiveis.findByUsuarioIdAndPessoaId(usuarioId, pessoaId)).saldo();
	}

}
