package br.com.contadinho.cartoes.service;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.contadinho.cartoes.Compras;
import br.com.contadinho.cartoes.domain.Cartao;
import br.com.contadinho.cartoes.domain.Fatura;
import br.com.contadinho.cartoes.domain.Lancamento;
import br.com.contadinho.cartoes.repository.CartaoRepositorio;
import br.com.contadinho.cartoes.repository.FaturaRepositorio;
import br.com.contadinho.cartoes.repository.LancamentoRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link Compras} para os outros módulos. */
@Service
@Transactional(readOnly = true)
public class ComprasServico implements Compras {

	private final LancamentoRepositorio lancamentos;

	private final FaturaRepositorio faturas;

	private final CartaoRepositorio cartoes;

	public ComprasServico(LancamentoRepositorio lancamentos, FaturaRepositorio faturas, CartaoRepositorio cartoes) {
		this.lancamentos = lancamentos;
		this.faturas = faturas;
		this.cartoes = cartoes;
	}

	@Override
	public List<Compra> buscar(UUID usuarioId, Collection<UUID> lancamentoIds) {
		var itens = lancamentos.findByUsuarioIdAndIdIn(usuarioId, lancamentoIds);
		var faturasPorId = faturas.findAllById(itens.stream().map(Lancamento::getFaturaId).distinct().toList()).stream()
			.collect(Collectors.toMap(Fatura::getId, Function.identity()));
		var cartoesPorId = cartoes.findAllById(itens.stream().map(Lancamento::getCartaoId).distinct().toList()).stream()
			.collect(Collectors.toMap(Cartao::getId, Function.identity()));
		return itens.stream().map(l -> {
			var cartao = cartoesPorId.get(l.getCartaoId());
			var vencimento = faturasPorId.get(l.getFaturaId()).getVencimento();
			return new Compra(l.getId(), l.getData(), l.getDescricao(), l.getValor(), l.getParcelaAtual(),
					l.getParcelaTotal(), cartao.getApelido() + " •• " + cartao.getUltimos4(), YearMonth.from(vencimento));
		}).toList();
	}

}
