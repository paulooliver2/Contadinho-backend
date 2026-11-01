package br.com.contadinho.receber.service;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.contadinho.receber.AReceber;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link AReceber} para os outros módulos. */
@Service
public class AReceberServico implements AReceber {

	private final ReceberServico receber;

	public AReceberServico(ReceberServico receber) {
		this.receber = receber;
	}

	@Override
	@Transactional(readOnly = true)
	public SituacaoAReceber situacao(UUID usuarioId) {
		var devem = receber.resumo(usuarioId).pessoas().stream().filter(s -> s.saldo().signum() > 0).toList();
		var original = devem.stream().map(ReceberServico.Situacao::original).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new SituacaoAReceber(devem.stream().map(ReceberServico.Situacao::saldo).reduce(BigDecimal.ZERO,
				BigDecimal::add), original,
				devem.stream()
					.map(s -> new Devedor(s.pessoaId(), s.saldo(), s.status() == ReceberServico.Status.PAGOU_PARTE))
					.toList());
	}

}
