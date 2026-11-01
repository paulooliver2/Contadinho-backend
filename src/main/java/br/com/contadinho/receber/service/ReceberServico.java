package br.com.contadinho.receber.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import br.com.contadinho.cartoes.Compras;
import br.com.contadinho.comum.ErroApi;
import br.com.contadinho.pessoas.Pessoas;
import br.com.contadinho.receber.domain.Cobranca;
import br.com.contadinho.receber.domain.Emprestimo;
import br.com.contadinho.receber.domain.Pagamento;
import br.com.contadinho.receber.domain.Recebivel;
import br.com.contadinho.receber.repository.CobrancaRepositorio;
import br.com.contadinho.receber.repository.EmprestimoRepositorio;
import br.com.contadinho.receber.repository.PagamentoRepositorio;
import br.com.contadinho.receber.repository.RecebivelRepositorio;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quanto cada pessoa deve, de compras e empréstimos (HU-18), o que ela já devolveu (HU-19) e quando foi cobrada
 * (HU-20). Nada é guardado: tudo sai dos recebíveis e dos
 * pagamentos não desfeitos (RN-39).
 */
@Service
@Transactional(readOnly = true)
public class ReceberServico {

	public static final String PESSOA_NAO_ENCONTRADA = "Pessoa não encontrada.";

	public static final String PAGAMENTO_NAO_ENCONTRADO = "Pagamento não encontrado.";

	public static final String MAIOR_QUE_O_DEVIDO = "Valor maior que o devido.";

	public static final String SEM_SALDO = "Essa pessoa não tem saldo a receber.";

	public static final String DATA_FUTURA = "A data não pode ser depois de hoje.";

	private final RecebivelRepositorio recebiveis;

	private final PagamentoRepositorio pagamentos;

	private final EmprestimoRepositorio emprestimos;

	private final CobrancaRepositorio cobrancas;

	private final Compras compras;

	private final Pessoas pessoas;

	private final Clock clock;

	public ReceberServico(RecebivelRepositorio recebiveis, PagamentoRepositorio pagamentos,
			EmprestimoRepositorio emprestimos, CobrancaRepositorio cobrancas, Compras compras, Pessoas pessoas,
			Clock clock) {
		this.recebiveis = recebiveis;
		this.pagamentos = pagamentos;
		this.emprestimos = emprestimos;
		this.cobrancas = cobrancas;
		this.compras = compras;
		this.pessoas = pessoas;
		this.clock = clock;
	}

	public enum Status {
		PENDENTE, PAGOU_PARTE, PAGO
	}

	/**
	 * RN-29. {@code proximoVencimento}: o do recebível ainda em aberto que vence primeiro, depois de abater os
	 * pagamentos (RN-30); null quando não há nada a receber ou só "Sem data" (RN-32). {@code ultimaCobranca}: quando
	 * o dono abriu a última cobrança no WhatsApp (HU-20.4); null se nunca cobrou.
	 */
	public record Situacao(UUID pessoaId, BigDecimal original, BigDecimal recebido, BigDecimal saldo, Status status,
			LocalDate proximoVencimento, Instant ultimaCobranca) {

		Situacao cobradaEm(Instant quando) {
			return new Situacao(pessoaId, original, recebido, saldo, status, proximoVencimento, quando);
		}
	}

	public record Resumo(BigDecimal aReceber, BigDecimal recebido, List<Situacao> pessoas) {
	}

	/** {@code emAberto}: o que falta da compra depois do abatimento (RN-30); zero quando já foi paga. */
	public record CompraDaPessoa(Compras.Compra compra, BigDecimal emAberto) {
	}

	/** Uma parcela do empréstimo; {@code emAberto} como em {@link CompraDaPessoa}. */
	public record ParcelaDaPessoa(short numero, BigDecimal valor, LocalDate vencimento, BigDecimal emAberto) {
	}

	public record EmprestimoDaPessoa(Emprestimo emprestimo, List<ParcelaDaPessoa> parcelas) {
	}

	public record Historico(Situacao situacao, List<CompraDaPessoa> compras, List<EmprestimoDaPessoa> emprestimos,
			List<Pagamento> pagamentos) {
	}

	/** Só quem tem recebíveis ou pagamentos; da que vence primeiro para a última, e as sem vencimento no fim (HU-18). */
	public Resumo resumo(UUID usuarioId) {
		var recebiveisPorPessoa = recebiveis.findByUsuarioId(usuarioId).stream()
			.collect(Collectors.groupingBy(Recebivel::getPessoaId));
		var pagamentosPorPessoa = pagamentos.findByUsuarioIdAndDesfeitoEmIsNull(usuarioId).stream()
			.collect(Collectors.groupingBy(Pagamento::getPessoaId));
		var cobradas = cobrancas.ultimas(usuarioId).stream()
			.collect(Collectors.toMap(CobrancaRepositorio.Ultima::getPessoaId, CobrancaRepositorio.Ultima::getEnviadaEm));
		var situacoes = Stream.concat(recebiveisPorPessoa.keySet().stream(), pagamentosPorPessoa.keySet().stream())
			.distinct()
			.map(p -> situacao(p, recebiveisPorPessoa.getOrDefault(p, List.of()),
					pagamentosPorPessoa.getOrDefault(p, List.of()))
				.cobradaEm(cobradas.get(p)))
			.sorted(Comparator.comparing(Situacao::proximoVencimento, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(Situacao::saldo, Comparator.reverseOrder()))
			.toList();
		var aReceber = situacoes.stream().map(Situacao::saldo).filter(s -> s.signum() > 0)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		var recebido = situacoes.stream().map(Situacao::recebido).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new Resumo(aReceber, recebido, situacoes);
	}

	/**
	 * HU-18.3: as compras atribuídas, da fatura mais recente para a mais antiga; os empréstimos e os pagamentos, do
	 * mais recente.
	 */
	public Historico historico(UUID usuarioId, UUID pessoaId) {
		existe(usuarioId, pessoaId);
		var daPessoa = recebiveis.findByUsuarioIdAndPessoaId(usuarioId, pessoaId);
		var pagos = pagamentosDa(usuarioId, pessoaId);
		var aberto = emAberto(daPessoa, total(pagos));
		var porLancamento = daPessoa.stream().filter(r -> r.getLancamentoId() != null)
			.collect(Collectors.toMap(Recebivel::getLancamentoId, Function.identity()));
		var lista = compras.buscar(usuarioId, porLancamento.keySet()).stream()
			.sorted(Comparator.comparing(Compras.Compra::fatura).thenComparing(Compras.Compra::data).reversed())
			.map(c -> new CompraDaPessoa(c, aberto.get(porLancamento.get(c.id()).getId())))
			.toList();
		var parcelas = daPessoa.stream().filter(r -> r.getEmprestimoId() != null)
			.sorted(Comparator.comparing(Recebivel::getParcela))
			.collect(Collectors.groupingBy(Recebivel::getEmprestimoId));
		var deEmprestimos = emprestimos.findByUsuarioIdAndPessoaIdOrderByCriadoEmDesc(usuarioId, pessoaId).stream()
			.map(e -> new EmprestimoDaPessoa(e, parcelas.getOrDefault(e.getId(), List.of()).stream()
				.map(r -> new ParcelaDaPessoa(r.getParcela(), r.getValor(), r.getVencimento(), aberto.get(r.getId())))
				.toList()))
			.toList();
		var cobrada = cobrancas.findFirstByUsuarioIdAndPessoaIdOrderByEnviadaEmDesc(usuarioId, pessoaId)
			.map(Cobranca::getEnviadaEm)
			.orElse(null);
		return new Historico(situacao(pessoaId, daPessoa, pagos).cobradaEm(cobrada), lista, deEmprestimos, pagos);
	}

	/** HU-19.1 e RN-30: parcial ou o saldo inteiro, nunca mais que ele (HU-19.4). */
	@Transactional
	public Pagamento registrar(UUID usuarioId, UUID pessoaId, BigDecimal valor, LocalDate recebidoEm) {
		existe(usuarioId, pessoaId);
		var hoje = LocalDate.now(clock);
		var data = recebidoEm == null ? hoje : recebidoEm;
		if (data.isAfter(hoje)) {
			throw ErroApi.campos(Map.of("recebidoEm", DATA_FUTURA));
		}
		var saldo = situacao(pessoaId, recebiveis.findByUsuarioIdAndPessoaId(usuarioId, pessoaId),
				pagamentosDa(usuarioId, pessoaId)).saldo();
		if (saldo.signum() <= 0) {
			throw new ErroApi(HttpStatus.CONFLICT, SEM_SALDO);
		}
		if (valor.compareTo(saldo) > 0) {
			throw ErroApi.campos(Map.of("valor", MAIOR_QUE_O_DEVIDO));
		}
		return pagamentos.save(new Pagamento(usuarioId, pessoaId, valor, data, Instant.now(clock)));
	}

	/**
	 * HU-20.4: o dono abriu a cobrança no WhatsApp. Guarda o saldo daquele momento; sem saldo, não há o que cobrar.
	 */
	@Transactional
	public Cobranca cobrar(UUID usuarioId, UUID pessoaId) {
		existe(usuarioId, pessoaId);
		var saldo = situacao(pessoaId, recebiveis.findByUsuarioIdAndPessoaId(usuarioId, pessoaId),
				pagamentosDa(usuarioId, pessoaId)).saldo();
		if (saldo.signum() <= 0) {
			throw new ErroApi(HttpStatus.CONFLICT, SEM_SALDO);
		}
		return cobrancas.save(new Cobranca(usuarioId, pessoaId, saldo, Instant.now(clock)));
	}

	/** HU-19.3: o pagamento sai do saldo e os abatimentos são refeitos no próximo cálculo. */
	@Transactional
	public void desfazer(UUID usuarioId, UUID pagamentoId) {
		var pagamento = pagamentos.findByIdAndUsuarioId(pagamentoId, usuarioId).filter(p -> !p.isDesfeito())
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, PAGAMENTO_NAO_ENCONTRADO));
		pagamento.desfazer(Instant.now(clock));
	}

	private void existe(UUID usuarioId, UUID pessoaId) {
		if (!pessoas.existe(usuarioId, pessoaId)) {
			throw new ErroApi(HttpStatus.NOT_FOUND, PESSOA_NAO_ENCONTRADA);
		}
	}

	private List<Pagamento> pagamentosDa(UUID usuarioId, UUID pessoaId) {
		return pagamentos.findByUsuarioIdAndPessoaIdAndDesfeitoEmIsNullOrderByRecebidoEmDescCriadoEmDesc(usuarioId,
				pessoaId);
	}

	public static Situacao situacao(UUID pessoaId, List<Recebivel> daPessoa, List<Pagamento> pagos) {
		var original = daPessoa.stream().map(Recebivel::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
		var recebido = total(pagos);
		var saldo = original.subtract(recebido);
		Status status;
		if (saldo.signum() <= 0) {
			status = Status.PAGO;
		}
		else {
			status = recebido.signum() == 0 ? Status.PENDENTE : Status.PAGOU_PARTE;
		}
		var aberto = emAberto(daPessoa, recebido);
		var proximo = saldo.signum() <= 0 ? null : daPessoa.stream()
			.filter(r -> aberto.get(r.getId()).signum() > 0)
			.map(Recebivel::getVencimento)
			.filter(Objects::nonNull)
			.min(Comparator.naturalOrder())
			.orElse(null);
		return new Situacao(pessoaId, original, recebido, saldo, status, proximo, null);
	}

	/**
	 * RN-30: os pagamentos e os estornos (RN-20) abatem do recebível que vence primeiro; os "Sem data" (RN-32) ficam
	 * por último. Devolve, por recebível, o que ainda falta; estorno fica com zero.
	 */
	static Map<UUID, BigDecimal> emAberto(List<Recebivel> daPessoa, BigDecimal recebido) {
		var credito = daPessoa.stream().map(Recebivel::getValor).filter(v -> v.signum() < 0)
			.reduce(recebido, (a, v) -> a.subtract(v));
		var aberto = new HashMap<UUID, BigDecimal>();
		var ordem = daPessoa.stream()
			.sorted(Comparator.comparing(Recebivel::getVencimento, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(Recebivel::getId))
			.toList();
		for (var r : ordem) {
			if (r.getValor().signum() <= 0) {
				aberto.put(r.getId(), BigDecimal.ZERO);
				continue;
			}
			var abatido = credito.min(r.getValor());
			aberto.put(r.getId(), r.getValor().subtract(abatido));
			credito = credito.subtract(abatido);
		}
		return aberto;
	}

	private static BigDecimal total(List<Pagamento> pagos) {
		return pagos.stream().map(Pagamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

}
