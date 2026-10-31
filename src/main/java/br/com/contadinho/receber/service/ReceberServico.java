package br.com.contadinho.receber.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.contadinho.cartoes.Compras;
import br.com.contadinho.comum.ErroApi;
import br.com.contadinho.pessoas.Pessoas;
import br.com.contadinho.receber.domain.Recebivel;
import br.com.contadinho.receber.repository.RecebivelRepositorio;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quanto cada pessoa deve (HU-18). Nada é guardado: tudo sai dos recebíveis (RN-39). Os pagamentos entram com a
 * HU-19; até lá, o recebido é zero.
 */
@Service
@Transactional(readOnly = true)
public class ReceberServico {

	public static final String PESSOA_NAO_ENCONTRADA = "Pessoa não encontrada.";

	private final RecebivelRepositorio recebiveis;

	private final Compras compras;

	private final Pessoas pessoas;

	public ReceberServico(RecebivelRepositorio recebiveis, Compras compras, Pessoas pessoas) {
		this.recebiveis = recebiveis;
		this.compras = compras;
		this.pessoas = pessoas;
	}

	public enum Status {
		PENDENTE, PAGOU_PARTE, PAGO
	}

	/**
	 * RN-29. {@code proximoVencimento}: o do recebível ainda em aberto que vence primeiro; null quando não há
	 * nada a receber ou só "Sem data" (RN-32).
	 */
	public record Situacao(UUID pessoaId, BigDecimal original, BigDecimal recebido, BigDecimal saldo, Status status,
			LocalDate proximoVencimento) {
	}

	public record Resumo(BigDecimal aReceber, BigDecimal recebido, List<Situacao> pessoas) {
	}

	public record Historico(Situacao situacao, List<Compras.Compra> compras) {
	}

	/** Só quem tem recebíveis; da que vence primeiro para a última, e as sem vencimento no fim (HU-18). */
	public Resumo resumo(UUID usuarioId) {
		var situacoes = recebiveis.findByUsuarioId(usuarioId).stream()
			.collect(Collectors.groupingBy(Recebivel::getPessoaId))
			.entrySet().stream()
			.map(e -> situacao(e.getKey(), e.getValue()))
			.sorted(Comparator.comparing(Situacao::proximoVencimento, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(Situacao::saldo, Comparator.reverseOrder()))
			.toList();
		var aReceber = situacoes.stream().map(Situacao::saldo).filter(s -> s.signum() > 0)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		var recebido = situacoes.stream().map(Situacao::recebido).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new Resumo(aReceber, recebido, situacoes);
	}

	/** HU-18.3: as compras atribuídas, da fatura mais recente para a mais antiga. */
	public Historico historico(UUID usuarioId, UUID pessoaId) {
		if (!pessoas.existe(usuarioId, pessoaId)) {
			throw new ErroApi(HttpStatus.NOT_FOUND, PESSOA_NAO_ENCONTRADA);
		}
		var daPessoa = recebiveis.findByUsuarioIdAndPessoaId(usuarioId, pessoaId);
		var ids = daPessoa.stream().map(Recebivel::getLancamentoId).filter(Objects::nonNull).toList();
		var porId = compras.buscar(usuarioId, ids).stream()
			.collect(Collectors.toMap(Compras.Compra::id, Function.identity()));
		var lista = ids.stream().map(porId::get).filter(Objects::nonNull)
			.sorted(Comparator.comparing(Compras.Compra::fatura).thenComparing(Compras.Compra::data).reversed())
			.toList();
		return new Historico(situacao(pessoaId, daPessoa), lista);
	}

	public static Situacao situacao(UUID pessoaId, List<Recebivel> daPessoa) {
		var original = daPessoa.stream().map(Recebivel::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
		var recebido = BigDecimal.ZERO;
		var saldo = original.subtract(recebido);
		Status status;
		if (saldo.signum() <= 0) {
			status = Status.PAGO;
		}
		else {
			status = recebido.signum() == 0 ? Status.PENDENTE : Status.PAGOU_PARTE;
		}
		var proximo = saldo.signum() <= 0 ? null : daPessoa.stream()
			.filter(r -> r.getValor().signum() > 0)
			.map(Recebivel::getVencimento)
			.filter(Objects::nonNull)
			.min(Comparator.naturalOrder())
			.orElse(null);
		return new Situacao(pessoaId, original, recebido, saldo, status, proximo);
	}

}
