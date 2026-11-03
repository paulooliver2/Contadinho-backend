package br.com.contadinho.cartoes.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.contadinho.cartoes.domain.CartaoAdicional;
import br.com.contadinho.cartoes.domain.Lancamento;
import br.com.contadinho.cartoes.repository.CartaoAdicionalRepositorio;
import br.com.contadinho.cartoes.repository.LancamentoRepositorio;
import br.com.contadinho.comum.ErroApi;
import br.com.contadinho.pessoas.Pessoas;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Os cartões adicionais de cada cartão e de quem é cada um (HU-38, RN-19). */
@Service
@Transactional(readOnly = true)
public class AdicionalServico {

	static final String FINAL_DO_TITULAR = "Esse é o final do próprio cartão. Informe o final do adicional.";

	static final String FINAL_REPETIDO = "Esse final já está cadastrado neste cartão.";

	static final String ADICIONAL_NAO_ENCONTRADO = "Adicional não encontrado.";

	private final FaturaServico faturas;

	private final CartaoAdicionalRepositorio adicionais;

	private final LancamentoRepositorio lancamentos;

	private final Pessoas pessoas;

	public AdicionalServico(FaturaServico faturas, CartaoAdicionalRepositorio adicionais, LancamentoRepositorio lancamentos,
			Pessoas pessoas) {
		this.faturas = faturas;
		this.adicionais = adicionais;
		this.lancamentos = lancamentos;
		this.pessoas = pessoas;
	}

	/** HU-38.3: se os arquivos já importados neste cartão trazem o final de cada compra. */
	public enum TrazFinal {
		SIM, NAO, SEM_IMPORTACAO
	}

	public record Adicionais(TrazFinal trazFinal, List<CartaoAdicional> lista) {
	}

	public Adicionais listar(UUID usuarioId, UUID cartaoId) {
		faturas.cartao(usuarioId, cartaoId);
		TrazFinal trazFinal;
		if (lancamentos.existsByCartaoIdAndFinalCartaoIsNotNull(cartaoId)) {
			trazFinal = TrazFinal.SIM;
		}
		else if (lancamentos.existsByCartaoIdAndOrigem(cartaoId, Lancamento.Origem.ARQUIVO)) {
			trazFinal = TrazFinal.NAO;
		}
		else {
			trazFinal = TrazFinal.SEM_IMPORTACAO;
		}
		return new Adicionais(trazFinal, adicionais.findByUsuarioIdAndCartaoIdOrderByUltimos4(usuarioId, cartaoId));
	}

	@Transactional
	public CartaoAdicional cadastrar(UUID usuarioId, UUID cartaoId, String ultimos4, UUID pessoaId) {
		var cartao = faturas.cartao(usuarioId, cartaoId);
		if (ultimos4.equals(cartao.getUltimos4())) {
			throw ErroApi.campos(Map.of("ultimos4", FINAL_DO_TITULAR));
		}
		if (adicionais.existsByCartaoIdAndUltimos4(cartaoId, ultimos4)) {
			throw ErroApi.campos(Map.of("ultimos4", FINAL_REPETIDO));
		}
		if (!pessoas.podeEscolher(usuarioId, pessoaId)) {
			throw ErroApi.campos(Map.of("pessoaId", ResponsavelServico.PESSOA_FORA_DA_LISTA));
		}
		return adicionais.save(new CartaoAdicional(usuarioId, cartaoId, ultimos4, pessoaId));
	}

	@Transactional
	public void remover(UUID usuarioId, UUID cartaoId, UUID id) {
		adicionais.delete(adicionais.findByIdAndUsuarioIdAndCartaoId(id, usuarioId, cartaoId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, ADICIONAL_NAO_ENCONTRADO)));
	}

	/**
	 * RN-19: final do cartão para a pessoa, usado na importação. A pessoa arquivada fica de fora (RN-36), e a compra
	 * chega com o dono.
	 */
	public Map<String, UUID> pessoaPorFinal(UUID usuarioId, UUID cartaoId) {
		return adicionais.findByUsuarioIdAndCartaoIdOrderByUltimos4(usuarioId, cartaoId).stream()
			.filter(a -> pessoas.podeEscolher(usuarioId, a.getPessoaId()))
			.collect(Collectors.toMap(CartaoAdicional::getUltimos4, CartaoAdicional::getPessoaId));
	}

}
