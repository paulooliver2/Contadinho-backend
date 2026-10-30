package br.com.contadinho.cartoes;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.comum.ErroApi;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faturas do cartão e compras digitadas (HU-47).
 */
@Service
@Transactional
class FaturaServico {

	static final String CARTAO_NAO_ENCONTRADO = "Cartão não encontrado.";

	static final String COMPRA_NAO_ENCONTRADA = "Compra não encontrada.";

	static final String COMPRA_DO_ARQUIVO = "Esta compra veio do arquivo do banco: data, descrição e valor não mudam.";

	private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

	private final CartaoRepositorio cartoes;

	private final FaturaRepositorio faturas;

	private final LancamentoRepositorio lancamentos;

	private final Clock clock;

	FaturaServico(CartaoRepositorio cartoes, FaturaRepositorio faturas, LancamentoRepositorio lancamentos, Clock clock) {
		this.cartoes = cartoes;
		this.faturas = faturas;
		this.lancamentos = lancamentos;
		this.clock = clock;
	}

	/** Uma fatura como o app mostra; sem lançamentos, as datas vêm do cálculo da RN-58. */
	record Vista(Cartao.Ciclo ciclo, boolean aberta, BigDecimal total, List<Lancamento> lancamentos) {
	}

	@Transactional(readOnly = true)
	Vista ver(UUID usuarioId, UUID cartaoId, YearMonth mes) {
		var cartao = cartao(usuarioId, cartaoId);
		var aberta = mes.equals(cartao.mesAberto(LocalDate.now(clock)));
		return gravada(cartaoId, mes).map(fatura -> {
			var itens = lancamentos.findByFaturaIdOrderByDataDescDescricao(fatura.getId());
			var total = itens.stream().map(Lancamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
			return new Vista(new Cartao.Ciclo(mes, fatura.getFechamento(), fatura.getVencimento()), aberta, total, itens);
		}).orElseGet(() -> new Vista(cartao.ciclo(mes), aberta, BigDecimal.ZERO, List.of()));
	}

	Lancamento adicionar(UUID usuarioId, UUID cartaoId, YearMonth mes, Lancamento.Compra compra) {
		var cartao = cartao(usuarioId, cartaoId);
		var fatura = gravada(cartaoId, mes).orElseGet(() -> faturas.save(new Fatura(usuarioId, cartaoId, cartao.ciclo(mes))));
		conferir(compra, fatura);
		return lancamentos.save(Lancamento.digitado(usuarioId, cartaoId, fatura.getId(), compra));
	}

	Lancamento corrigir(UUID usuarioId, UUID lancamentoId, Lancamento.Compra compra) {
		var lancamento = digitado(usuarioId, lancamentoId);
		conferir(compra, faturas.getReferenceById(lancamento.getFaturaId()));
		lancamento.corrigir(compra);
		return lancamento;
	}

	void excluir(UUID usuarioId, UUID lancamentoId) {
		lancamentos.delete(digitado(usuarioId, lancamentoId));
	}

	private Cartao cartao(UUID usuarioId, UUID cartaoId) {
		return cartoes.findByIdAndUsuarioIdAndEncerradoEmIsNull(cartaoId, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, CARTAO_NAO_ENCONTRADO));
	}

	private Optional<Fatura> gravada(UUID cartaoId, YearMonth mes) {
		return faturas.findFirstByCartaoIdAndVencimentoBetweenOrderByVencimento(cartaoId, mes.atDay(1), mes.atEndOfMonth());
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
