package br.com.contadinho.cartoes;

import java.util.UUID;

import br.com.contadinho.comum.ErroApi;
import br.com.contadinho.pessoas.Pessoas;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * De quem é cada compra da fatura (HU-08): o dono ou uma pessoa cadastrada (RN-15), com a regra das próximas
 * parcelas (RN-17).
 */
@Service
@Transactional
class ResponsavelServico {

	static final String PESSOA_FORA_DA_LISTA = "Essa pessoa não está na sua lista. Escolha outra.";

	private final LancamentoRepositorio lancamentos;

	private final RegraParcelaRepositorio regras;

	private final Pessoas pessoas;

	ResponsavelServico(LancamentoRepositorio lancamentos, RegraParcelaRepositorio regras, Pessoas pessoas) {
		this.lancamentos = lancamentos;
		this.regras = regras;
		this.pessoas = pessoas;
	}

	/** {@code proximas}: quantas parcelas já gravadas em faturas seguintes mudaram junto. */
	record Atribuicao(Lancamento lancamento, int proximas) {
	}

	/**
	 * @param pessoaId null devolve a compra ao dono
	 * @param proximas o dono disse que as próximas parcelas da mesma compra também são dessa pessoa (RN-17)
	 */
	Atribuicao atribuir(UUID usuarioId, UUID lancamentoId, UUID pessoaId, boolean proximas) {
		var lancamento = lancamentos.findByIdAndUsuarioId(lancamentoId, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, FaturaServico.COMPRA_NAO_ENCONTRADA));
		// Arquivada não aparece para escolha (RN-36), mas a compra que já era dela pode ser salva de novo.
		if (pessoaId != null && !pessoaId.equals(lancamento.getPessoaId()) && !pessoas.podeEscolher(usuarioId, pessoaId)) {
			throw new ErroApi(HttpStatus.BAD_REQUEST, PESSOA_FORA_DA_LISTA);
		}
		lancamento.atribuir(pessoaId);
		if (!proximas || !temProximas(lancamento)) {
			return new Atribuicao(lancamento, 0);
		}
		var regra = regras.findByCartaoIdAndParcelaTotal(lancamento.getCartaoId(), lancamento.getParcelaTotal().shortValue())
			.stream()
			.filter(r -> r.mesmaCompra(lancamento))
			.findFirst()
			.orElse(null);
		if (regra == null) {
			regra = regras.save(new RegraParcela(lancamento, pessoaId));
		}
		else {
			regra.atualizar(lancamento, pessoaId);
		}
		// Parcelas que já chegaram em faturas seguintes (importadas antes) mudam agora.
		int mudaram = 0;
		for (var seguinte : lancamentos.findByCartaoIdAndParcelaTotalAndParcelaAtualGreaterThan(lancamento.getCartaoId(),
				lancamento.getParcelaTotal().shortValue(), lancamento.getParcelaAtual().shortValue())) {
			if (regra.cobre(seguinte)) {
				seguinte.atribuir(pessoaId);
				mudaram++;
			}
		}
		return new Atribuicao(lancamento, mudaram);
	}

	/** RN-17: uma parcela nova (importada ou digitada) chega com o responsável da regra da compra, se houver. */
	void aplicarRegra(Lancamento nova) {
		if (nova.getParcelaTotal() == null) {
			return;
		}
		regras.findByCartaoIdAndParcelaTotal(nova.getCartaoId(), nova.getParcelaTotal().shortValue())
			.stream()
			.filter(r -> r.cobre(nova))
			.findFirst()
			.ifPresent(r -> nova.atribuir(r.getPessoaId()));
	}

	private static boolean temProximas(Lancamento l) {
		return l.getParcelaTotal() != null && l.getParcelaAtual() < l.getParcelaTotal();
	}

}
