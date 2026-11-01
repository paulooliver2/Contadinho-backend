package br.com.contadinho.gastos.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.contadinho.cartoes.Cartoes;
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
 * cada mês, criada na primeira vez que o mês é aberto.
 */
@Service
public class ContaFixaServico {

	public static final String CONTA_NAO_ENCONTRADA = "Conta fixa não encontrada.";

	public static final String CATEGORIA_FORA_DA_LISTA = "Escolha uma categoria da lista.";

	public static final String ESCOLHA_O_CARTAO = "Escolha o cartão.";

	public static final String CONTA_ENCERRADA = "Essa conta já foi encerrada.";

	private final ContaFixaRepositorio contas;

	private final ContaFixaValorRepositorio valores;

	private final OcorrenciaContaRepositorio ocorrencias;

	private final Categorias categorias;

	private final Cartoes cartoes;

	private final Clock clock;

	public ContaFixaServico(ContaFixaRepositorio contas, ContaFixaValorRepositorio valores,
			OcorrenciaContaRepositorio ocorrencias, Categorias categorias, Cartoes cartoes, Clock clock) {
		this.contas = contas;
		this.valores = valores;
		this.ocorrencias = ocorrencias;
		this.categorias = categorias;
		this.cartoes = cartoes;
		this.clock = clock;
	}

	/** HU-11.1. {@code cartaoId} só conta quando a forma é cartão (RN-22). */
	public record Dados(String descricao, BigDecimal valor, UUID categoriaId, int diaVencimento,
			ContaFixa.FormaPagamento formaPagamento, UUID cartaoId, Boolean essencial) {
	}

	/** Uma conta no mês: a ocorrência e o cadastro, com o valor que vale a partir deste mês. */
	public record ContaDoMes(OcorrenciaConta ocorrencia, ContaFixa conta, BigDecimal valorAtual) {

		public OcorrenciaConta.Status status() {
			return ocorrencia.status(conta.isNoCartao());
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

	/** HU-11.2: as contas do mês, pelo vencimento. Cria as ocorrências que faltam das contas ativas no mês. */
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
		return ocorrencias.findByUsuarioIdAndCompetencia(usuarioId, mes.atDay(1)).stream()
			.map(o -> new ContaDoMes(o, porId.get(o.getContaFixaId()), atuais.get(o.getContaFixaId())))
			.sorted(Comparator.comparing((ContaDoMes c) -> c.ocorrencia().getVencimento())
				.thenComparing(c -> c.conta().getDescricao()))
			.toList();
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
