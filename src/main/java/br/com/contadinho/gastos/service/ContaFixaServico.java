package br.com.contadinho.gastos.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.contadinho.cartoes.Cartoes;
import br.com.contadinho.cartoes.Compras;
import br.com.contadinho.categorias.Categorias;
import br.com.contadinho.comum.ErroApi;
import br.com.contadinho.gastos.domain.ContaFixa;
import br.com.contadinho.gastos.domain.ContaFixaValor;
import br.com.contadinho.gastos.domain.OcorrenciaConta;
import br.com.contadinho.gastos.repository.ContaFixaRepositorio;
import br.com.contadinho.gastos.repository.ContaFixaValorRepositorio;
import br.com.contadinho.gastos.repository.OcorrenciaContaRepositorio;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contas fixas (HU-11): cadastro, mudança de valor do mês atual em diante e encerramento (RN-21), e a ocorrência de
 * cada mês, criada na primeira vez que o mês é aberto. Marcar como paga e desfazer (HU-13). Conta paga no cartão
 * é ligada ao lançamento da fatura (HU-14).
 */
@Service
public class ContaFixaServico {

	public static final String CONTA_NAO_ENCONTRADA = "Conta fixa não encontrada.";

	public static final String CATEGORIA_FORA_DA_LISTA = "Escolha uma categoria da lista.";

	public static final String ESCOLHA_O_CARTAO = "Escolha o cartão.";

	public static final String CONTA_ENCERRADA = "Essa conta já foi encerrada.";

	public static final String OCORRENCIA_NAO_ENCONTRADA = "Conta do mês não encontrada.";

	public static final String PAGA_PELA_FATURA = "Conta no cartão é paga pela fatura.";

	public static final String DATA_FUTURA = "A data não pode ser depois de hoje.";

	public static final String SO_NO_CARTAO = "Só conta paga no cartão se liga à fatura.";

	public static final String COMPRA_DE_OUTRO_CARTAO = "Escolha uma compra do cartão desta conta.";

	public static final String COMPRA_JA_LIGADA = "Essa compra já paga outra conta fixa.";

	/** RN-23: a compra cai em até 5 dias do vencimento, com até R$ 1 de diferença. */
	static final int DIAS_DO_VENCIMENTO = 5;

	static final BigDecimal DIFERENCA_DE_VALOR = BigDecimal.ONE;

	/** HU-14.2: ao ligar à mão, as compras do cartão até um mês antes ou depois do vencimento. */
	static final int DIAS_DE_BUSCA = 31;

	private final ContaFixaRepositorio contas;

	private final ContaFixaValorRepositorio valores;

	private final OcorrenciaContaRepositorio ocorrencias;

	private final Categorias categorias;

	private final Cartoes cartoes;

	private final Compras compras;

	private final Clock clock;

	public ContaFixaServico(ContaFixaRepositorio contas, ContaFixaValorRepositorio valores,
			OcorrenciaContaRepositorio ocorrencias, Categorias categorias, Cartoes cartoes, Compras compras, Clock clock) {
		this.contas = contas;
		this.valores = valores;
		this.ocorrencias = ocorrencias;
		this.categorias = categorias;
		this.cartoes = cartoes;
		this.compras = compras;
		this.clock = clock;
	}

	/** HU-11.1. {@code cartaoId} só conta quando a forma é cartão (RN-22). */
	public record Dados(String descricao, BigDecimal valor, UUID categoriaId, int diaVencimento,
			ContaFixa.FormaPagamento formaPagamento, UUID cartaoId, Boolean essencial) {
	}

	/**
	 * Uma conta no mês: a ocorrência e o cadastro, com o valor que vale a partir deste mês. {@code compra} é o
	 * lançamento da fatura que a paga (HU-14), quando ligada.
	 */
	public record ContaDoMes(OcorrenciaConta ocorrencia, ContaFixa conta, BigDecimal valorAtual, Compras.Compra compra) {

		public OcorrenciaConta.Status status() {
			return ocorrencia.status(conta.isNoCartao());
		}

		/** Ligada à fatura, vale o lançamento, nunca a ocorrência (RN-23); senão, o pago ou o previsto (RN-25). */
		public BigDecimal valor() {
			return compra != null ? compra.valor() : ocorrencia.valor();
		}
	}

	/**
	 * A primeira ocorrência é a do mês atual; se o vencimento deste mês já passou, a do próximo (quem cadastra o
	 * aluguel no dia 20 já pagou o do dia 5).
	 */
	@Transactional
	public ContaFixa cadastrar(UUID usuarioId, Dados dados) {
		validar(usuarioId, dados, null);
		var hoje = LocalDate.now(clock);
		var mes = YearMonth.from(hoje);
		if (mes.atDay(Math.min(dados.diaVencimento(), mes.lengthOfMonth())).isBefore(hoje)) {
			mes = mes.plusMonths(1);
		}
		var conta = new ContaFixa(usuarioId, mes);
		aplicar(conta, dados);
		contas.saveAndFlush(conta);
		valores.save(new ContaFixaValor(conta.getId(), mes, dados.valor()));
		return conta;
	}

	/**
	 * HU-11.3: o valor novo vale do mês atual em diante. As ocorrências deste mês e dos próximos que ainda não foram
	 * pagas nem ligadas à fatura acompanham o cadastro; as passadas ficam como estavam.
	 */
	@Transactional
	public ContaFixa alterar(UUID usuarioId, UUID id, Dados dados) {
		var conta = conta(usuarioId, id);
		if (conta.isEncerrada()) {
			throw new ErroApi(HttpStatus.CONFLICT, CONTA_ENCERRADA);
		}
		validar(usuarioId, dados, conta);
		aplicar(conta, dados);
		// Conta que ainda não começou: o valor novo vale desde o primeiro mês dela.
		var mes = conta.getPrimeiroMes().isAfter(mesAtual()) ? conta.getPrimeiroMes() : mesAtual();
		var vigentes = new ArrayList<>(valores.findByContaFixaIdIn(List.of(id)));
		vigentes.stream().filter(v -> v.getDesde().equals(mes)).findFirst()
			.ifPresentOrElse(v -> v.trocar(dados.valor()), () -> {
				if (valorEm(vigentes, mes).compareTo(dados.valor()) != 0) {
					vigentes.add(valores.save(new ContaFixaValor(id, mes, dados.valor())));
				}
			});
		ocorrencias.findByContaFixaIdAndCompetenciaGreaterThanEqual(id, mes.atDay(1)).stream()
			.filter(OcorrenciaConta::emAberto)
			.forEach(o -> o.acompanhar(valorEm(vigentes, o.getMes()), conta.vencimentoEm(o.getMes())));
		return conta;
	}

	/**
	 * HU-11.4: para de gerar a partir do próximo mês. Ocorrências de meses seguintes já abertas, e ainda em aberto,
	 * somem; o histórico fica.
	 */
	@Transactional
	public ContaFixa encerrar(UUID usuarioId, UUID id) {
		var conta = conta(usuarioId, id);
		var hoje = LocalDate.now(clock);
		conta.encerrar(hoje);
		var seguinte = YearMonth.from(hoje).plusMonths(1).atDay(1);
		ocorrencias.findByContaFixaIdAndCompetenciaGreaterThanEqual(id, seguinte).stream()
			.filter(OcorrenciaConta::emAberto)
			.forEach(ocorrencias::delete);
		return conta;
	}

	/**
	 * HU-11.2: as contas do mês, pelo vencimento. Cria as ocorrências que faltam das contas ativas no mês e marca
	 * como pago o débito automático que já venceu (RN-24).
	 */
	@Transactional
	public List<ContaDoMes> doMes(UUID usuarioId, YearMonth mes) {
		var todas = contas.findByUsuarioId(usuarioId);
		var porConta = valores.findByContaFixaIdIn(todas.stream().map(ContaFixa::getId).toList()).stream()
			.collect(Collectors.groupingBy(ContaFixaValor::getContaFixaId));
		for (var conta : todas) {
			if (conta.ativaEm(mes)) {
				ocorrencias.gerar(usuarioId, conta.getId(), mes.atDay(1), conta.vencimentoEm(mes),
						valorEm(porConta.get(conta.getId()), mes));
			}
		}
		var porId = todas.stream().collect(Collectors.toMap(ContaFixa::getId, Function.identity()));
		var atual = mesAtual();
		var atuais = new HashMap<UUID, BigDecimal>();
		porConta.forEach((id, lista) -> atuais.put(id, valorEm(lista, atual.isBefore(mes) ? mes : atual)));
		var hoje = LocalDate.now(clock);
		var doMes = ocorrencias.findByUsuarioIdAndCompetencia(usuarioId, mes.atDay(1));
		doMes.stream()
			.filter(o -> porId.get(o.getContaFixaId()).getFormaPagamento() == ContaFixa.FormaPagamento.DEBITO_AUTOMATICO)
			.filter(o -> o.debitaSozinha(hoje))
			.forEach(o -> o.pagar(o.getVencimento(), o.getValorPrevisto()));
		var ligadas = comprasLigadas(usuarioId, doMes);
		return doMes.stream()
			.map(o -> new ContaDoMes(o, porId.get(o.getContaFixaId()), atuais.get(o.getContaFixaId()),
					ligadas.get(o.getLancamentoId())))
			.sorted(Comparator.comparing((ContaDoMes c) -> c.ocorrencia().getVencimento())
				.thenComparing(c -> c.conta().getDescricao()))
			.toList();
	}

	/**
	 * HU-13.1: sem data, vale hoje; sem valor, o previsto. Valor diferente do previsto é o que conta no mês (RN-25).
	 * Conta no cartão não se paga aqui: ela entra pela fatura (RN-23).
	 */
	@Transactional
	public ContaDoMes pagar(UUID usuarioId, UUID ocorrenciaId, LocalDate em, BigDecimal valor) {
		var item = item(usuarioId, ocorrenciaId);
		if (item.conta().isNoCartao()) {
			throw new ErroApi(HttpStatus.CONFLICT, PAGA_PELA_FATURA);
		}
		var hoje = LocalDate.now(clock);
		var data = em == null ? hoje : em;
		if (data.isAfter(hoje)) {
			throw ErroApi.campos(Map.of("pagoEm", DATA_FUTURA));
		}
		var o = item.ocorrencia();
		o.pagar(data, valor == null ? o.getValorPrevisto() : valor);
		return item;
	}

	/** HU-13.3: volta a ficar a pagar; o débito automático desfeito não é marcado de novo (RN-24). */
	@Transactional
	public ContaDoMes desfazerPagamento(UUID usuarioId, UUID ocorrenciaId) {
		var item = item(usuarioId, ocorrenciaId);
		item.ocorrencia()
			.desfazerPagamento(item.conta().getFormaPagamento() == ContaFixa.FormaPagamento.DEBITO_AUTOMATICO);
		return item;
	}

	/**
	 * HU-14.1 (RN-23): cada conta fixa paga neste cartão é ligada à compra importada de valor até R$ 1 diferente,
	 * em até 5 dias do vencimento. Com mais de um par possível, ganham os de data mais perto e depois os de valor mais
	 * perto; cada compra paga uma conta só. A que não acha continua aguardando a fatura.
	 */
	@Transactional
	public void ligarImportadas(UUID usuarioId, UUID cartaoId, List<UUID> lancamentoIds) {
		var contasDoCartao = contas.findByUsuarioIdAndCartaoId(usuarioId, cartaoId).stream()
			.filter(ContaFixa::isNoCartao)
			.toList();
		var importadas = compras.buscar(usuarioId, lancamentoIds).stream().filter(c -> c.valor().signum() > 0).toList();
		if (contasDoCartao.isEmpty() || importadas.isEmpty()) {
			return;
		}
		var de = importadas.stream().map(Compras.Compra::data).min(Comparator.naturalOrder()).orElseThrow()
			.minusDays(DIAS_DO_VENCIMENTO);
		var ate = importadas.stream().map(Compras.Compra::data).max(Comparator.naturalOrder()).orElseThrow()
			.plusDays(DIAS_DO_VENCIMENTO);
		// O mês só ganha ocorrências quando é aberto; a fatura pode chegar antes disso.
		for (var mes = YearMonth.from(de); !mes.isAfter(YearMonth.from(ate)); mes = mes.plusMonths(1)) {
			doMes(usuarioId, mes);
		}
		var abertas = ocorrencias
			.findByContaFixaIdInAndVencimentoBetween(contasDoCartao.stream().map(ContaFixa::getId).toList(), de, ate)
			.stream()
			.filter(OcorrenciaConta::emAberto)
			.toList();
		var jaPagam = ocorrencias.findByUsuarioIdAndLancamentoIdIn(usuarioId, lancamentoIds).stream()
			.map(OcorrenciaConta::getLancamentoId)
			.collect(Collectors.toSet());

		record Par(OcorrenciaConta ocorrencia, Compras.Compra compra, long dias, BigDecimal diferenca) {
		}
		var pares = new ArrayList<Par>();
		for (var o : abertas) {
			for (var c : importadas) {
				var dias = Math.abs(ChronoUnit.DAYS.between(c.data(), o.getVencimento()));
				var diferenca = c.valor().subtract(o.getValorPrevisto()).abs();
				if (!jaPagam.contains(c.id()) && dias <= DIAS_DO_VENCIMENTO
						&& diferenca.compareTo(DIFERENCA_DE_VALOR) <= 0) {
					pares.add(new Par(o, c, dias, diferenca));
				}
			}
		}
		pares.sort(Comparator.comparingLong(Par::dias).thenComparing(Par::diferenca));
		var usadas = new HashSet<UUID>();
		for (var p : pares) {
			if (p.ocorrencia().emAberto() && !usadas.contains(p.compra().id())) {
				usadas.add(p.compra().id());
				p.ocorrencia().ligar(p.compra().id());
			}
		}
	}

	/**
	 * HU-14.2: compras do cartão da conta perto do vencimento que ainda não pagam outra conta, as de valor mais
	 * perto do previsto primeiro.
	 */
	@Transactional(readOnly = true)
	public List<Compras.Compra> candidatas(UUID usuarioId, UUID ocorrenciaId) {
		var item = item(usuarioId, ocorrenciaId);
		var conta = noCartao(item);
		var o = item.ocorrencia();
		var doCartao = compras.doCartao(usuarioId, conta.getCartaoId(), o.getVencimento().minusDays(DIAS_DE_BUSCA),
				o.getVencimento().plusDays(DIAS_DE_BUSCA));
		var jaPagam = ocorrencias
			.findByUsuarioIdAndLancamentoIdIn(usuarioId, doCartao.stream().map(Compras.Compra::id).toList())
			.stream()
			.filter(outra -> !outra.getId().equals(o.getId()))
			.map(OcorrenciaConta::getLancamentoId)
			.collect(Collectors.toSet());
		return doCartao.stream()
			.filter(c -> !jaPagam.contains(c.id()))
			.sorted(Comparator.comparing((Compras.Compra c) -> c.valor().subtract(o.getValorPrevisto()).abs())
				.thenComparingLong(c -> Math.abs(ChronoUnit.DAYS.between(c.data(), o.getVencimento()))))
			.toList();
	}

	/** HU-14.2: liga à mão a uma compra do mesmo cartão, mesmo fora da regra automática. Passa a "na fatura". */
	@Transactional
	public ContaDoMes ligar(UUID usuarioId, UUID ocorrenciaId, UUID lancamentoId) {
		var item = item(usuarioId, ocorrenciaId);
		var conta = noCartao(item);
		var compra = compras.buscar(usuarioId, List.of(lancamentoId)).stream()
			.filter(c -> c.cartaoId().equals(conta.getCartaoId()))
			.findFirst()
			.orElseThrow(() -> ErroApi.campos(Map.of("lancamentoId", COMPRA_DE_OUTRO_CARTAO)));
		var pagaOutra = ocorrencias.findByUsuarioIdAndLancamentoIdIn(usuarioId, List.of(lancamentoId)).stream()
			.anyMatch(x -> !x.getId().equals(ocorrenciaId));
		if (pagaOutra) {
			throw new ErroApi(HttpStatus.CONFLICT, COMPRA_JA_LIGADA);
		}
		item.ocorrencia().ligar(lancamentoId);
		return new ContaDoMes(item.ocorrencia(), conta, item.valorAtual(), compra);
	}

	/** HU-14.2: a compra não era esta conta; volta a aguardar a fatura. */
	@Transactional
	public ContaDoMes desligar(UUID usuarioId, UUID ocorrenciaId) {
		var item = item(usuarioId, ocorrenciaId);
		noCartao(item);
		item.ocorrencia().desligar();
		return new ContaDoMes(item.ocorrencia(), item.conta(), item.valorAtual(), null);
	}

	private static ContaFixa noCartao(ContaDoMes item) {
		if (!item.conta().isNoCartao()) {
			throw new ErroApi(HttpStatus.CONFLICT, SO_NO_CARTAO);
		}
		return item.conta();
	}

	/** Lançamento → compra, das ocorrências ligadas à fatura. */
	private Map<UUID, Compras.Compra> comprasLigadas(UUID usuarioId, Collection<OcorrenciaConta> lista) {
		var ids = lista.stream().map(OcorrenciaConta::getLancamentoId).filter(Objects::nonNull).toList();
		if (ids.isEmpty()) {
			// Quem pergunta pela ocorrência não ligada passa null: o Map.of() não aceita.
			return new HashMap<>();
		}
		return compras.buscar(usuarioId, ids).stream().collect(Collectors.toMap(Compras.Compra::id, Function.identity()));
	}

	private ContaDoMes item(UUID usuarioId, UUID ocorrenciaId) {
		var o = ocorrencias.findByIdAndUsuarioId(ocorrenciaId, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, OCORRENCIA_NAO_ENCONTRADA));
		var conta = conta(usuarioId, o.getContaFixaId());
		var mes = mesAtual().isBefore(o.getMes()) ? o.getMes() : mesAtual();
		var compra = o.getLancamentoId() == null ? null : comprasLigadas(usuarioId, List.of(o)).get(o.getLancamentoId());
		return new ContaDoMes(o, conta, valorEm(valores.findByContaFixaIdIn(List.of(conta.getId())), mes), compra);
	}

	/**
	 * RN-22: cartão só com um cartão ativo do dono. Categoria arquivada não entra em conta nova, mas a conta que já
	 * tinha pode ser salva com ela (RN-27).
	 */
	private void validar(UUID usuarioId, Dados dados, ContaFixa atual) {
		var mantem = atual != null && dados.categoriaId().equals(atual.getCategoriaId());
		if (!mantem && !categorias.podeEscolher(usuarioId, dados.categoriaId())) {
			throw ErroApi.campos(Map.of("categoriaId", CATEGORIA_FORA_DA_LISTA));
		}
		if (dados.formaPagamento() == ContaFixa.FormaPagamento.CARTAO
				&& (dados.cartaoId() == null || !cartoes.ativo(usuarioId, dados.cartaoId()))) {
			throw ErroApi.campos(Map.of("cartaoId", ESCOLHA_O_CARTAO));
		}
	}

	private static void aplicar(ContaFixa conta, Dados dados) {
		conta.alterar(dados.descricao(), dados.categoriaId(), dados.diaVencimento(), dados.formaPagamento(),
				dados.cartaoId(), dados.essencial());
	}

	/** O valor que vale no mês: o da vigência mais recente que já começou. */
	static BigDecimal valorEm(List<ContaFixaValor> vigencias, YearMonth mes) {
		return vigencias.stream()
			.filter(v -> !v.getDesde().isAfter(mes))
			.max(Comparator.comparing(ContaFixaValor::getDesde))
			.or(() -> vigencias.stream().min(Comparator.comparing(ContaFixaValor::getDesde)))
			.map(ContaFixaValor::getValor)
			.orElseThrow();
	}

	private ContaFixa conta(UUID usuarioId, UUID id) {
		return contas.findByIdAndUsuarioId(id, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, CONTA_NAO_ENCONTRADA));
	}

	private YearMonth mesAtual() {
		return YearMonth.now(clock);
	}

}
