package br.com.contadinho.cartoes.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.contadinho.cartoes.Faturas;
import br.com.contadinho.cartoes.domain.Cartao;
import br.com.contadinho.cartoes.domain.Fatura;
import br.com.contadinho.cartoes.domain.Lancamento;
import br.com.contadinho.cartoes.repository.CartaoRepositorio;
import br.com.contadinho.cartoes.repository.FaturaRepositorio;
import br.com.contadinho.cartoes.repository.LancamentoRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link Faturas} para os outros módulos. */
@Service
@Transactional(readOnly = true)
public class FaturasDoMesServico implements Faturas {

	private final FaturaRepositorio faturas;

	private final LancamentoRepositorio lancamentos;

	private final CartaoRepositorio cartoes;

	public FaturasDoMesServico(FaturaRepositorio faturas, LancamentoRepositorio lancamentos, CartaoRepositorio cartoes) {
		this.faturas = faturas;
		this.lancamentos = lancamentos;
		this.cartoes = cartoes;
	}

	@Override
	public List<FaturaDoMes> queVencemEm(UUID usuarioId, YearMonth mes) {
		var doMes = faturas.findByUsuarioIdAndVencimentoBetween(usuarioId, mes.atDay(1), mes.atEndOfMonth());
		if (doMes.isEmpty()) {
			return List.of();
		}
		var porFatura = lancamentos.findByFaturaIdIn(doMes.stream().map(Fatura::getId).toList()).stream()
			.collect(Collectors.groupingBy(Lancamento::getFaturaId));
		var cartoesPorId = cartoes.findAllById(doMes.stream().map(Fatura::getCartaoId).distinct().toList()).stream()
			.collect(Collectors.toMap(Cartao::getId, Function.identity()));
		return doMes.stream().map(f -> {
			var itens = porFatura.getOrDefault(f.getId(), List.of());
			var cartao = cartoesPorId.get(f.getCartaoId());
			return new FaturaDoMes(f.getCartaoId(), cartao.getApelido() + " •• " + cartao.getUltimos4(), f.getVencimento(),
					soma(itens), soma(itens.stream().filter(l -> l.getPessoaId() == null).toList()));
		}).sorted(Comparator.comparing(FaturaDoMes::vencimento).thenComparing(FaturaDoMes::cartao)).toList();
	}

	private static BigDecimal soma(List<Lancamento> itens) {
		return itens.stream().map(Lancamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

}
