package br.com.contadinho.cartoes.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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

	private final FaturaServico fatura;

	private final Clock clock;

	public FaturasDoMesServico(FaturaRepositorio faturas, LancamentoRepositorio lancamentos, CartaoRepositorio cartoes,
			FaturaServico fatura, Clock clock) {
		this.faturas = faturas;
		this.lancamentos = lancamentos;
		this.cartoes = cartoes;
		this.fatura = fatura;
		this.clock = clock;
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
			return new FaturaDoMes(f.getCartaoId(), nome(cartao), f.getVencimento(), soma(itens),
					soma(itens.stream().filter(l -> l.getPessoaId() == null).toList()));
		}).sorted(Comparator.comparing(FaturaDoMes::vencimento).thenComparing(FaturaDoMes::cartao)).toList();
	}

	@Override
	public List<FaturaAtual> atuais(UUID usuarioId) {
		var hoje = LocalDate.now(clock);
		return cartoes.findByUsuarioIdAndEncerradoEmIsNullOrderByCriadoEm(usuarioId).stream().map(cartao -> {
			var mes = YearMonth.from(hoje);
			if (cartao.ciclo(mes).vencimento().isBefore(hoje)) {
				mes = mes.plusMonths(1);
			}
			var vista = fatura.ver(usuarioId, cartao.getId(), mes);
			return new FaturaAtual(cartao.getId(), nome(cartao), mes, vista.ciclo().vencimento(), !vista.aberta(),
					vista.total(), vista.partes().stream().map(p -> new Parte(p.pessoaId(), p.total())).toList());
		}).toList();
	}

	private static String nome(Cartao cartao) {
		return cartao.getApelido() + " •• " + cartao.getUltimos4();
	}

	private static BigDecimal soma(List<Lancamento> itens) {
		return itens.stream().map(Lancamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

}
