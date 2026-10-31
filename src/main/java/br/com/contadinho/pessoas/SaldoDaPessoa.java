package br.com.contadinho.pessoas;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Quanto a pessoa ainda deve ao dono (RN-29). Quem calcula é o módulo A receber; as pessoas só perguntam, para
 * travar arquivar com saldo (RN-36) sem depender daquele módulo.
 */
public interface SaldoDaPessoa {

	BigDecimal saldo(UUID usuarioId, UUID pessoaId);

}
