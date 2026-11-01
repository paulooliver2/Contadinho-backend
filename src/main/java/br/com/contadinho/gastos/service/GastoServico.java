package br.com.contadinho.gastos.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
import br.com.contadinho.gastos.domain.Gasto;
import br.com.contadinho.gastos.repository.GastoRepositorio;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gastos avulsos (HU-12) e a correção deles (HU-46). O gasto no cartão aguarda o lançamento da fatura e é ligado a
 * ele pela regra da conta fixa, contando da data do gasto (RN-26): ao salvar, se a fatura já chegou, ou ao importar.
 */
@Service
public class GastoServico {

	public static final String GASTO_NAO_ENCONTRADO = "Gasto não encontrado.";

	private final GastoRepositorio gastos;

	private final Categorias categorias;

	private final Cartoes cartoes;

	private final Compras compras;

	private final LigacaoFatura ligacao;

	private final Clock clock;

	public GastoServico(GastoRepositorio gastos, Categorias categorias, Cartoes cartoes, Compras compras,
			LigacaoFatura ligacao, Clock clock) {
		this.gastos = gastos;
		this.categorias = categorias;
		this.cartoes = cartoes;
		this.compras = compras;
		this.ligacao = ligacao;
		this.clock = clock;
	}

	/** HU-12.1. Sem data, vale hoje. {@code cartaoId} só conta quando a forma é cartão. */
	public record Dados(String descricao, BigDecimal valor, LocalDate data, UUID categoriaId,
			Gasto.FormaPagamento formaPagamento, UUID cartaoId, Boolean essencial) {
	}

	/** O gasto e, no cartão, o lançamento da fatura que o paga. */
	public record GastoDoMes(Gasto gasto, Compras.Compra compra) {

		/** Ligado à fatura, vale o lançamento. */
		public BigDecimal valor() {
			return compra != null ? compra.valor() : gasto.getValor();
		}
	}

	@Transactional
	public GastoDoMes cadastrar(UUID usuarioId, Dados dados) {
		var gasto = new Gasto(usuarioId);
		return salvar(usuarioId, gasto, dados, null);
	}

	/** HU-46.1. Categoria arquivada que o gasto já tinha continua valendo (RN-27). */
	@Transactional
	public GastoDoMes alterar(UUID usuarioId, UUID id, Dados dados) {
		var gasto = gasto(usuarioId, id);
		return salvar(usuarioId, gasto, dados, gasto.getCategoriaId());
	}

	/** HU-46.1 e HU-46.3: o lançamento da fatura fica como estava. */
	@Transactional
	public void excluir(UUID usuarioId, UUID id) {
		gastos.delete(gasto(usuarioId, id));
	}

	/** Os gastos do mês, pela data (RN-37), do mais recente para o mais antigo. */
	@Transactional(readOnly = true)
	public List<GastoDoMes> doMes(UUID usuarioId, YearMonth mes) {
		var lista = gastos.findByUsuarioIdAndDataBetween(usuarioId, mes.atDay(1), mes.atEndOfMonth());
		var ligadas = comprasLigadas(usuarioId, lista);
		return lista.stream()
			.map(g -> new GastoDoMes(g, ligadas.get(g.getLancamentoId())))
			.sorted(Comparator.comparing((GastoDoMes g) -> g.gasto().getData()).reversed()
				.thenComparing(g -> g.gasto().getDescricao()))
			.toList();
	}

	/** RN-26: a fatura importada trouxe compras; os gastos do cartão que aguardam procuram a sua. */
	@Transactional
	public void ligarImportadas(UUID usuarioId, UUID cartaoId, List<UUID> lancamentoIds) {
		var importadas = compras.buscar(usuarioId, lancamentoIds);
		if (importadas.isEmpty()) {
			return;
		}
		var de = importadas.stream().map(Compras.Compra::data).min(Comparator.naturalOrder()).orElseThrow()
			.minusDays(LigacaoFatura.DIAS);
		var ate = importadas.stream().map(Compras.Compra::data).max(Comparator.naturalOrder()).orElseThrow()
			.plusDays(LigacaoFatura.DIAS);
		var aguardando = gastos.findByUsuarioIdAndCartaoIdAndLancamentoIdIsNullAndDataBetween(usuarioId, cartaoId, de, ate)
			.stream()
			.map(g -> new LigacaoFatura.Pendente<>(g, g.getData(), g.getValor()))
			.toList();
		LigacaoFatura.parear(aguardando, importadas, ligacao.usados(usuarioId, lancamentoIds))
			.forEach(p -> p.item().ligar(p.compra().id()));
	}

	private GastoDoMes salvar(UUID usuarioId, Gasto novo, Dados dados, UUID categoriaAtual) {
		var hoje = LocalDate.now(clock);
		var data = dados.data() == null ? hoje : dados.data();
		validar(usuarioId, dados, data, hoje, categoriaAtual);
		novo.alterar(dados.descricao(), dados.valor(), data, dados.categoriaId(), dados.formaPagamento(),
				dados.cartaoId(), dados.essencial());
		// Com o id já preenchido, o save faz merge: a instância gerenciada é a que volta.
		var gasto = gastos.saveAndFlush(novo);
		if (!gasto.isNoCartao()) {
			return new GastoDoMes(gasto, null);
		}
		// A fatura pode já ter chegado: procura entre as compras do cartão em volta da data.
		var doCartao = compras.doCartao(usuarioId, gasto.getCartaoId(), data.minusDays(LigacaoFatura.DIAS),
				data.plusDays(LigacaoFatura.DIAS));
		var pendente = new LigacaoFatura.Pendente<>(gasto, data, gasto.getValor());
		var par = LigacaoFatura.parear(List.of(pendente), doCartao,
				ligacao.usados(usuarioId, doCartao.stream().map(Compras.Compra::id).toList()));
		par.forEach(p -> gasto.ligar(p.compra().id()));
		return new GastoDoMes(gasto, par.isEmpty() ? null : par.get(0).compra());
	}

	/** O cartão precisa ser um cartão ativo do dono (RN-22). */
	private void validar(UUID usuarioId, Dados dados, LocalDate data, LocalDate hoje, UUID categoriaAtual) {
		var mantem = dados.categoriaId().equals(categoriaAtual);
		if (!mantem && !categorias.podeEscolher(usuarioId, dados.categoriaId())) {
			throw ErroApi.campos(Map.of("categoriaId", ContaFixaServico.CATEGORIA_FORA_DA_LISTA));
		}
		if (dados.formaPagamento() == Gasto.FormaPagamento.CARTAO
				&& (dados.cartaoId() == null || !cartoes.ativo(usuarioId, dados.cartaoId()))) {
			throw ErroApi.campos(Map.of("cartaoId", ContaFixaServico.ESCOLHA_O_CARTAO));
		}
		if (data.isAfter(hoje)) {
			throw ErroApi.campos(Map.of("data", ContaFixaServico.DATA_FUTURA));
		}
	}

	private Map<UUID, Compras.Compra> comprasLigadas(UUID usuarioId, Collection<Gasto> lista) {
		var ids = lista.stream().map(Gasto::getLancamentoId).filter(Objects::nonNull).toList();
		if (ids.isEmpty()) {
			return new HashMap<>();
		}
		return compras.buscar(usuarioId, ids).stream().collect(Collectors.toMap(Compras.Compra::id, Function.identity()));
	}

	private Gasto gasto(UUID usuarioId, UUID id) {
		return gastos.findByIdAndUsuarioId(id, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, GASTO_NAO_ENCONTRADO));
	}

}
