package br.com.contadinho.receber;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** O que os outros módulos leem do A receber, como o Início (HU-24). */
public interface AReceber {

	SituacaoAReceber situacao(UUID usuarioId);

	/**
	 * @param total o que as pessoas ainda devem (RN-29)
	 * @param original o que foi registrado para quem ainda deve, antes dos pagamentos
	 * @param devedores só quem ainda deve, como em A receber: do vencimento mais próximo para o último
	 */
	record SituacaoAReceber(BigDecimal total, BigDecimal original, List<Devedor> devedores) {
	}

	/** {@code pagouParte}: já pagou algo e ainda deve (RN-30). */
	record Devedor(UUID pessoaId, BigDecimal saldo, boolean pagouParte) {
	}

}
