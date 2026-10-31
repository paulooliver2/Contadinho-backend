package br.com.contadinho.cartoes.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.cartoes.domain.Cartao;
import br.com.contadinho.cartoes.domain.Fatura;
import br.com.contadinho.cartoes.domain.Lancamento;
import br.com.contadinho.cartoes.repository.CartaoRepositorio;
import br.com.contadinho.cartoes.repository.FaturaRepositorio;
import br.com.contadinho.cartoes.repository.LancamentoRepositorio;
import br.com.contadinho.comum.ErroApi;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faturas do cartão e compras digitadas (HU-47).
 */
@Service
@Transactional
public class FaturaServico {

	public static final String CARTAO_NAO_ENCONTRADO = "Cartão não encontrado.";

	public static final String COMPRA_NAO_ENCONTRADA = "Compra não encontrada.";

	public static final String COMPRA_DO_ARQUIVO = "Esta compra veio do arquivo do banco: data, descrição e valor não mudam.";

	private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

	private final CartaoRepositorio cartoes;

	private final FaturaRepositorio faturas;

	private final LancamentoRepositorio lancamentos;

	private final ResponsavelServico responsaveis;

	private final Clock clock;

	public FaturaServico(CartaoRepositorio cartoes, FaturaRepositorio faturas, LancamentoRepositorio lancamentos,
			ResponsavelServico responsaveis, Clock clock) {
		this.cartoes = cartoes;
		this.faturas = faturas;
		this.lancamentos = lancamentos;
		this.responsaveis = responsaveis;
		this.clock = clock;
	}

	/** Uma fatura como o app mostra; sem lançamentos, as datas vêm do cálculo da RN-58. */
	public record Vista(Cartao.Ciclo ciclo, boolean aberta, BigDecimal total, List<Parte> partes, List<Lancamento> lancamentos) {
	}

	/** HU-10: quanto da fatura é de cada responsável; {@code pessoaId} null é o dono (RN-15). */
	public record Parte(UUID pessoaId, BigDecimal total, int quantidade) {
	}

	@Transactional(readOnly = true)
	public Vista ver(UUID usuarioId, UUID cartaoId, YearMonth mes) {
		var cartao = cartao(usuarioId, cartaoId);
		var aberta = mes.equals(cartao.mesAberto(LocalDate.now(clock)));
		return gravada(cartaoId, mes).map(fatura -> {
			var itens = lancamentos.findByFaturaIdOrderByDataDescDescricao(fatura.getId());
			var total = itens.stream().map(Lancamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
			return new Vista(new Cartao.Ciclo(mes, fatura.getFechamento(), fatura.getVencimento()), aberta, total,
					partes(itens), itens);
		}).orElseGet(() -> new Vista(cartao.ciclo(mes), aberta, BigDecimal.ZERO, List.of(), List.of()));
	}

	/**
	 * HU-10: cada compra está em exatamente uma parte, então a soma das partes é o total. O dono vem primeiro;
	 * as pessoas, da maior parte para a menor.
	 */
	private static List<Parte> partes(List<Lancamento> itens) {
		var porResponsavel = new HashMap<Optional<UUID>, Parte>();
		for (var l : itens) {
			porResponsavel.merge(Optional.ofNullable(l.getPessoaId()), new Parte(l.getPessoaId(), l.getValor(), 1),
					(a, b) -> new Parte(a.pessoaId(), a.total().add(b.total()), a.quantidade() + b.quantidade()));
		}
		return porResponsavel.values().stream()
			.sorted(Comparator.comparing((Parte p) -> p.pessoaId() != null).thenComparing(Parte::total, Comparator.reverseOrder()))
			.toList();
	}

	public Lancamento adicionar(UUID usuarioId, UUID cartaoId, YearMonth mes, Lancamento.Compra compra) {
		var fatura = gravarFatura(usuarioId, cartao(usuarioId, cartaoId), mes);
		conferir(compra, fatura);
		return responsaveis.gravarNova(Lancamento.digitado(usuarioId, cartaoId, fatura.getId(), compra));
	}

	public Lancamento corrigir(UUID usuarioId, UUID lancamentoId, Lancamento.Compra compra) {
		var lancamento = digitado(usuarioId, lancamentoId);
		conferir(compra, faturas.getReferenceById(lancamento.getFaturaId()));
		lancamento.corrigir(compra);
		responsaveis.valorMudou(lancamento);
		return lancamento;
	}

	public void excluir(UUID usuarioId, UUID lancamentoId) {
		lancamentos.delete(digitado(usuarioId, lancamentoId));
	}

	/** Cartão ativo do dono; de outro dono, ou encerrado, é 404 (RNF-01). */
	public Cartao cartao(UUID usuarioId, UUID cartaoId) {
		return cartoes.findByIdAndUsuarioIdAndEncerradoEmIsNull(cartaoId, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, CARTAO_NAO_ENCONTRADO));
	}

	public Optional<Fatura> gravada(UUID cartaoId, YearMonth mes) {
		return faturas.findFirstByCartaoIdAndVencimentoBetweenOrderByVencimento(cartaoId, mes.atDay(1), mes.atEndOfMonth());
	}

	/** As datas da fatura do mês: as gravadas, se ela já existe, ou as do cálculo (RN-58). */
	public Cartao.Ciclo ciclo(Cartao cartao, YearMonth mes) {
		return gravada(cartao.getId(), mes)
			.map(f -> new Cartao.Ciclo(mes, f.getFechamento(), f.getVencimento()))
			.orElseGet(() -> cartao.ciclo(mes));
	}

	public Fatura gravarFatura(UUID usuarioId, Cartao cartao, YearMonth mes) {
		return gravada(cartao.getId(), mes).orElseGet(() -> faturas.save(new Fatura(usuarioId, cartao.getId(), cartao.ciclo(mes))));
	}

	private Lancamento digitado(UUID usuarioId, UUID lancamentoId) {
		var lancamento = lancamentos.findByIdAndUsuarioId(lancamentoId, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, COMPRA_NAO_ENCONTRADA));
		if (lancamento.getOrigem() != Lancamento.Origem.MANUAL) {
			throw new ErroApi(HttpStatus.CONFLICT, COMPRA_DO_ARQUIVO);
		}
		return lancamento;
	}

	/** RN-60: as regras que dependem da fatura ou de mais de um campo. */
	private static void conferir(Lancamento.Compra compra, Fatura fatura) {
		var erros = new LinkedHashMap<String, String>();
		if (compra.data().isAfter(fatura.getFechamento())) {
			erros.put("data", "A fatura fecha em " + fatura.getFechamento().format(DIA_MES) + "; use uma data até lá.");
		}
		if (compra.valor().signum() == 0) {
			erros.put("valor", "Informe um valor diferente de zero.");
		}
		var atual = compra.parcelaAtual();
		var total = compra.parcelaTotal();
		if ((atual == null) != (total == null)) {
			erros.put("parcela", "Informe a parcela e o total, como 3/10.");
		}
		else if (atual != null && atual > total) {
			erros.put("parcela", "A parcela não pode passar do total.");
		}
		if (!erros.isEmpty()) {
			throw ErroApi.campos(erros);
		}
	}

}
