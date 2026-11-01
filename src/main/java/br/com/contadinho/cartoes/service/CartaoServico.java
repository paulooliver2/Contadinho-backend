package br.com.contadinho.cartoes.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import br.com.contadinho.cartoes.Cartoes;
import br.com.contadinho.cartoes.UsoDoCartao;
import br.com.contadinho.cartoes.domain.Cartao;
import br.com.contadinho.cartoes.repository.CartaoRepositorio;
import br.com.contadinho.cartoes.repository.FaturaRepositorio;
import br.com.contadinho.comum.ErroApi;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link Cartoes} para os outros módulos, e a edição, o encerramento e a exclusão (HU-45). */
@Service
@Transactional(readOnly = true)
public class CartaoServico implements Cartoes {

	static final String TEM_HISTORICO = "Este cartão já tem faturas, contas ou gastos. Encerre-o: o histórico continua nos totais e no A receber.";

	private final CartaoRepositorio cartoes;

	private final FaturaRepositorio faturas;

	private final List<UsoDoCartao> usos;

	private final Clock clock;

	public CartaoServico(CartaoRepositorio cartoes, FaturaRepositorio faturas, List<UsoDoCartao> usos, Clock clock) {
		this.cartoes = cartoes;
		this.faturas = faturas;
		this.usos = usos;
		this.clock = clock;
	}

	@Override
	public boolean ativo(UUID usuarioId, UUID cartaoId) {
		return cartoes.findByIdAndUsuarioIdAndEncerradoEmIsNull(cartaoId, usuarioId).isPresent();
	}

	/**
	 * HU-45.2: só sem nenhuma fatura gravada e sem conta ou gasto pago com ele. A HU fala em importação; a fatura
	 * gravada cobre as importações e também as compras digitadas, que são histórico do mesmo jeito.
	 */
	public boolean podeExcluir(UUID usuarioId, UUID cartaoId) {
		return !faturas.existsByCartaoId(cartaoId) && usos.stream().noneMatch(u -> u.usa(usuarioId, cartaoId));
	}

	@Transactional
	public Cartao alterar(UUID usuarioId, UUID cartaoId, Function<Cartao, String> apelido, int diaFechamento,
			int diaVencimento) {
		var cartao = ativoDoDono(usuarioId, cartaoId);
		cartao.alterar(apelido.apply(cartao), diaFechamento, diaVencimento);
		return cartao;
	}

	@Transactional
	public void encerrar(UUID usuarioId, UUID cartaoId) {
		ativoDoDono(usuarioId, cartaoId).encerrar(Instant.now(clock));
	}

	@Transactional
	public void excluir(UUID usuarioId, UUID cartaoId) {
		var cartao = ativoDoDono(usuarioId, cartaoId);
		if (!podeExcluir(usuarioId, cartaoId)) {
			throw new ErroApi(HttpStatus.CONFLICT, TEM_HISTORICO);
		}
		cartoes.delete(cartao);
	}

	private Cartao ativoDoDono(UUID usuarioId, UUID cartaoId) {
		return cartoes.findByIdAndUsuarioIdAndEncerradoEmIsNull(cartaoId, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, FaturaServico.CARTAO_NAO_ENCONTRADO));
	}

}
