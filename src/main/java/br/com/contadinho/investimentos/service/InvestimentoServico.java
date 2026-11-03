package br.com.contadinho.investimentos.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.UUID;
import java.util.regex.Pattern;

import br.com.contadinho.comum.ErroApi;
import br.com.contadinho.investimentos.domain.AtualizacaoFonte;
import br.com.contadinho.investimentos.domain.Cotacao;
import br.com.contadinho.investimentos.domain.DiasUteis;
import br.com.contadinho.investimentos.domain.Evolucao;
import br.com.contadinho.investimentos.domain.Investimento;
import br.com.contadinho.investimentos.domain.ValorAtual;
import br.com.contadinho.investimentos.repository.AtualizacaoFonteRepositorio;
import br.com.contadinho.investimentos.repository.CdiRepositorio;
import br.com.contadinho.investimentos.repository.CotacaoDiariaRepositorio;
import br.com.contadinho.investimentos.repository.CotacaoRepositorio;
import br.com.contadinho.investimentos.domain.Movimentacao;
import br.com.contadinho.investimentos.domain.Posicao;
import br.com.contadinho.investimentos.repository.InvestimentoRepositorio;
import br.com.contadinho.investimentos.repository.MovimentacaoRepositorio;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A carteira (HU-30): cadastra a posição de hoje e as movimentações. Nada calculado é gravado; a {@link Posicao}
 * reproduz as movimentações a cada leitura (RNF-05).
 */
@Service
@Transactional
public class InvestimentoServico {

	public static final String NAO_ENCONTRADO = "Investimento não encontrado.";

	public static final String SO_ACAO_FII = "Só ação pode ser FII.";

	public static final String CRIPTO_FORA = "A busca de cripto está fora do ar. Tente de novo em instantes.";

	private static final Pattern TICKER = Pattern.compile("[A-Z0-9]{4}\\d{1,2}");

	private static final Pattern FRACIONARIO = Pattern.compile("([A-Z0-9]{4}\\d{1,2})F");

	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final int ULTIMO_ANO_TESOURO = 2080;

	/** NUMERIC(20,8): quantidade e preço com até 12 dígitos inteiros. */
	private static final BigDecimal LIMITE_UNITARIO = new BigDecimal("1000000000000");

	/** NUMERIC(12,2): valores em reais abaixo de 10 bilhões. */
	private static final BigDecimal LIMITE_REAIS = new BigDecimal("10000000000");

	/** O título do Tesouro, como na planilha do Tesouro Transparente que a HU-31 vai ler. */
	public enum TituloTesouro {

		SELIC("Tesouro Selic"), IPCA("Tesouro IPCA+"), IPCA_JUROS("Tesouro IPCA+ com Juros Semestrais"),
		PREFIXADO("Tesouro Prefixado"), PREFIXADO_JUROS("Tesouro Prefixado com Juros Semestrais"),
		RENDA_MAIS("Tesouro Renda+ Aposentadoria Extra"), EDUCA_MAIS("Tesouro Educa+");

		private final String nome;

		TituloTesouro(String nome) {
			this.nome = nome;
		}

		public String nome() {
			return nome;
		}

	}

	/** O que o app manda ao cadastrar; os campos valem conforme o tipo. */
	public record Novo(Investimento.Tipo tipo, String codigo, BigDecimal quantidade, BigDecimal preco,
			BigDecimal valor, TituloTesouro tesouroTipo, Integer tesouroAno, Investimento.Produto produto,
			String emissor, Investimento.Indexador indexador, BigDecimal taxa, LocalDate dataAplicacao,
			LocalDate vencimento, Boolean fii) {
	}

	public record NovaMovimentacao(Movimentacao.Tipo tipo, LocalDate data, BigDecimal quantidade, BigDecimal preco,
			BigDecimal valor, Boolean total) {
	}

	/**
	 * A posição com o que foi calculado e as movimentações, da mais antiga para a mais nova. HU-31: o valor de hoje,
	 * a cotação usada, se está desatualizada, o valor da aplicação inicial e, na renda fixa encerrada, o resultado.
	 */
	public record Detalhe(Investimento investimento, Posicao posicao, List<Movimentacao> movimentacoes,
			ValorAtual.Resultado valor, Cotacao cotacao, boolean desatualizado, BigDecimal aplicadoInicial,
			BigDecimal resultado) {
	}

	private final InvestimentoRepositorio investimentos;

	private final MovimentacaoRepositorio movimentacoes;

	private final FonteAtivos fonte;

	private final Clock clock;

	private final CotacaoRepositorio cotacoes;

	private final CdiRepositorio cdis;

	private final AtualizacaoFonteRepositorio controles;

	private final CotacaoDiariaRepositorio diarias;

	public InvestimentoServico(InvestimentoRepositorio investimentos, MovimentacaoRepositorio movimentacoes,
			FonteAtivos fonte, Clock clock, CotacaoRepositorio cotacoes, CdiRepositorio cdis,
			AtualizacaoFonteRepositorio controles, CotacaoDiariaRepositorio diarias) {
		this.diarias = diarias;
		this.investimentos = investimentos;
		this.movimentacoes = movimentacoes;
		this.fonte = fonte;
		this.clock = clock;
		this.cotacoes = cotacoes;
		this.cdis = cdis;
		this.controles = controles;
	}

	@Transactional(readOnly = true)
	public List<Detalhe> carteira(UUID usuarioId) {
		var lista = investimentos.findByUsuarioIdOrderByCriadoEm(usuarioId);
		var todas = movimentacoes.findByInvestimentoIdIn(lista.stream().map(Investimento::getId).toList())
			.stream()
			.collect(java.util.stream.Collectors.groupingBy(Movimentacao::getInvestimentoId));
		return avaliar(lista, todas);
	}

	@Transactional(readOnly = true)
	public Detalhe detalhe(UUID usuarioId, UUID id) {
		var i = buscar(usuarioId, id);
		return detalhe(i, movimentacoes.findByInvestimentoId(i.getId()));
	}

	public Detalhe cadastrar(UUID usuarioId, Novo novo) {
		var agora = Instant.now(clock);
		var hoje = LocalDate.now(clock);
		var campos = new LinkedHashMap<String, String>();
		if (novo.tipo() == null) {
			throw ErroApi.campos(Map.of("tipo", "Escolha o tipo."));
		}
		if (Boolean.TRUE.equals(novo.fii()) && novo.tipo() != Investimento.Tipo.ACAO) {
			throw ErroApi.campos(Map.of("fii", SO_ACAO_FII));
		}
		Investimento investimento;
		Movimentacao inicial;
		switch (novo.tipo()) {
			case ACAO, CRIPTO -> {
				positivo(campos, "quantidade", novo.quantidade(), novo.tipo() == Investimento.Tipo.ACAO ? 0 : 8,
						LIMITE_UNITARIO);
				positivo(campos, "preco", novo.preco(), 8, LIMITE_UNITARIO);
				totalNoLimite(campos, novo.quantidade(), novo.preco());
				var achado = novo.tipo() == Investimento.Tipo.ACAO ? conferirTicker(campos, novo.codigo())
						: conferirCripto(campos, novo.codigo());
				falhar(campos);
				repetido(usuarioId, novo.tipo(), achado.codigo(), achado.exibicao());
				investimento = new Investimento(usuarioId, novo.tipo(), achado.codigo(), achado.nome(),
						achado.simbolo(), achado.conferido(), agora);
				inicial = new Movimentacao(investimento.getId(), usuarioId, Movimentacao.Tipo.INICIAL, hoje,
						novo.quantidade(), novo.preco(), null, false, agora);
			}
			case TESOURO -> {
				if (novo.tesouroTipo() == null) {
					campos.put("tesouroTipo", "Escolha o título.");
				}
				if (novo.tesouroAno() == null || novo.tesouroAno() < hoje.getYear()
						|| novo.tesouroAno() > ULTIMO_ANO_TESOURO) {
					campos.put("tesouroAno",
							"Informe o ano de vencimento, de " + hoje.getYear() + " a " + ULTIMO_ANO_TESOURO + ".");
				}
				positivo(campos, "quantidade", novo.quantidade(), 2, LIMITE_UNITARIO);
				positivo(campos, "valor", novo.valor(), 2, LIMITE_REAIS);
				falhar(campos);
				var codigo = novo.tesouroTipo().nome() + " " + novo.tesouroAno();
				repetido(usuarioId, Investimento.Tipo.TESOURO, codigo, codigo);
				investimento = new Investimento(usuarioId, Investimento.Tipo.TESOURO, codigo, codigo, null, true,
						agora);
				inicial = new Movimentacao(investimento.getId(), usuarioId, Movimentacao.Tipo.INICIAL, hoje,
						novo.quantidade(), null, novo.valor(), false, agora);
			}
			case RENDA_FIXA -> {
				if (novo.produto() == null) {
					campos.put("produto", "Escolha CDB, LCI ou LCA.");
				}
				var emissor = novo.emissor() == null ? "" : novo.emissor().trim();
				if (emissor.isEmpty() || emissor.length() > 80) {
					campos.put("emissor", "Informe o banco emissor, com até 80 letras.");
				}
				positivo(campos, "valor", novo.valor(), 2, LIMITE_REAIS);
				if (novo.dataAplicacao() == null) {
					campos.put("dataAplicacao", "Informe a data da aplicação.");
				}
				else if (novo.dataAplicacao().isAfter(hoje)) {
					campos.put("dataAplicacao", "A data não pode ser no futuro.");
				}
				if (novo.indexador() == null) {
					campos.put("indexador", "Escolha % do CDI ou prefixado.");
				}
				if (novo.taxa() == null || novo.taxa().signum() <= 0 || novo.taxa().compareTo(BigDecimal.valueOf(1000)) >= 0
						|| casas(novo.taxa()) > 4) {
					campos.put("taxa", "Informe a taxa, maior que zero e abaixo de 1000.");
				}
				if (novo.vencimento() == null) {
					campos.put("vencimento", "Informe o vencimento.");
				}
				else if (novo.dataAplicacao() != null && !novo.vencimento().isAfter(novo.dataAplicacao())) {
					campos.put("vencimento", "O vencimento precisa ser depois da aplicação.");
				}
				falhar(campos);
				investimento = Investimento.rendaFixa(usuarioId, novo.produto(), emissor, novo.indexador(),
						novo.taxa(), novo.vencimento(), agora);
				inicial = new Movimentacao(investimento.getId(), usuarioId, Movimentacao.Tipo.INICIAL,
						novo.dataAplicacao(), null, null, novo.valor(), false, agora);
			}
			default -> throw new IllegalStateException();
		}
		investimento.setFii(Boolean.TRUE.equals(novo.fii()));
		investimentos.save(investimento);
		movimentacoes.save(inicial);
		return detalhe(investimento, List.of(inicial));
	}

	public Detalhe movimentar(UUID usuarioId, UUID id, NovaMovimentacao nova) {
		var i = buscar(usuarioId, id);
		var hoje = LocalDate.now(clock);
		var existentes = movimentacoes.findByInvestimentoId(i.getId());
		var campos = new LinkedHashMap<String, String>();
		if (nova.tipo() == null || nova.tipo() == Movimentacao.Tipo.INICIAL) {
			throw ErroApi.campos(Map.of("tipo", "Escolha aportar ou resgatar."));
		}
		var rendaFixa = i.getTipo() == Investimento.Tipo.RENDA_FIXA;
		if (rendaFixa && nova.tipo() == Movimentacao.Tipo.APORTE) {
			throw ErroApi.campos(Map.of("tipo", "Para aplicar de novo, cadastre outra aplicação."));
		}
		var data = nova.data() == null ? hoje : nova.data();
		var dataInicial = existentes.stream()
			.filter(m -> m.getTipo() == Movimentacao.Tipo.INICIAL)
			.map(Movimentacao::getData)
			.findFirst()
			.orElse(data);
		if (data.isAfter(hoje)) {
			campos.put("data", "A data não pode ser no futuro.");
		}
		else if (data.isBefore(dataInicial)) {
			campos.put("data", "A data não pode ser antes da posição inicial (" + DIA.format(dataInicial) + ").");
		}
		var casas = switch (i.getTipo()) {
			case ACAO -> 0;
			case TESOURO -> 2;
			default -> 8;
		};
		BigDecimal quantidade = null;
		BigDecimal preco = null;
		BigDecimal valor = null;
		if (rendaFixa) {
			positivo(campos, "valor", nova.valor(), 2, LIMITE_REAIS);
			valor = nova.valor();
		}
		else {
			positivo(campos, "quantidade", nova.quantidade(), casas, LIMITE_UNITARIO);
			quantidade = nova.quantidade();
			if (nova.tipo() == Movimentacao.Tipo.APORTE) {
				if (i.getTipo() == Investimento.Tipo.TESOURO) {
					positivo(campos, "valor", nova.valor(), 2, LIMITE_REAIS);
					valor = nova.valor();
				}
				else {
					positivo(campos, "preco", nova.preco(), 8, LIMITE_UNITARIO);
					totalNoLimite(campos, nova.quantidade(), nova.preco());
					preco = nova.preco();
				}
			}
		}
		falhar(campos);
		var m = new Movimentacao(i.getId(), usuarioId, nova.tipo(), data, quantidade, preco, valor,
				rendaFixa && Boolean.TRUE.equals(nova.total()), Instant.now(clock));
		var depois = new ArrayList<>(existentes);
		depois.add(m);
		Posicao.problema(i.getTipo(), depois).ifPresent(problema -> {
			throw ErroApi.campos(Map.of(rendaFixa ? "valor" : "quantidade", problema));
		});
		movimentacoes.save(m);
		return detalhe(i, depois);
	}

	public Detalhe excluirMovimentacao(UUID usuarioId, UUID id, UUID movimentacaoId) {
		var i = buscar(usuarioId, id);
		var existentes = movimentacoes.findByInvestimentoId(i.getId());
		var alvo = existentes.stream()
			.filter(m -> m.getId().equals(movimentacaoId))
			.findFirst()
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, "Movimentação não encontrada."));
		if (alvo.getTipo() == Movimentacao.Tipo.INICIAL) {
			throw new ErroApi(HttpStatus.BAD_REQUEST,
					"A posição inicial não se exclui. Para corrigir, exclua o investimento e cadastre de novo.");
		}
		var depois = existentes.stream().filter(m -> m != alvo).toList();
		Posicao.problema(i.getTipo(), depois).ifPresent(problema -> {
			throw new ErroApi(HttpStatus.BAD_REQUEST, "Sem esta movimentação, " + Character.toLowerCase(problema.charAt(0))
					+ problema.substring(1));
		});
		movimentacoes.delete(alvo);
		return detalhe(i, depois);
	}

	/** HU-32: Ação/ETF ou FII, só para a distribuição por classe. */
	public Detalhe alterarClasse(UUID usuarioId, UUID id, boolean fii) {
		var i = buscar(usuarioId, id);
		if (i.getTipo() != Investimento.Tipo.ACAO) {
			throw ErroApi.campos(Map.of("fii", SO_ACAO_FII));
		}
		i.setFii(fii);
		return detalhe(i, movimentacoes.findByInvestimentoId(i.getId()));
	}

	/**
	 * HU-32: o valor de cada dia do período e o rendimento, descontados aportes e resgates. Só lê o que está guardado
	 * (preços do dia, cotação atual e CDI); nunca chama fonte externa.
	 */
	@Transactional(readOnly = true)
	public Evolucao.Resultado evolucao(UUID usuarioId, Evolucao.Periodo periodo) {
		var hoje = LocalDate.now(clock);
		var lista = investimentos.findByUsuarioIdOrderByCriadoEm(usuarioId);
		var porInvestimento = movimentacoes.findByInvestimentoIdIn(lista.stream().map(Investimento::getId).toList())
			.stream()
			.collect(java.util.stream.Collectors.groupingBy(Movimentacao::getInvestimentoId));
		var diariosPorTipo = new java.util.HashMap<Investimento.Tipo, Map<String, NavigableMap<LocalDate, BigDecimal>>>();
		var atuais = new java.util.HashMap<String, BigDecimal>();
		for (var tipo : List.of(Investimento.Tipo.ACAO, Investimento.Tipo.CRIPTO, Investimento.Tipo.TESOURO)) {
			var codigos = lista.stream().filter(i -> i.getTipo() == tipo).map(Investimento::getCodigo).toList();
			diariosPorTipo.put(tipo, diarias.porCodigo(tipo, codigos, hoje));
			if (!codigos.isEmpty()) {
				cotacoes.findByTipoAndCodigoIn(tipo, codigos)
					.stream()
					.filter(c -> c.getPreco() != null)
					.forEach(c -> atuais.put(tipo + "|" + c.getCodigo(), c.getPreco()));
			}
		}
		var ativos = lista.stream().map(i -> {
			var precos = i.getTipo() == Investimento.Tipo.RENDA_FIXA ? null
					: diariosPorTipo.get(i.getTipo()).get(i.getCodigo());
			return new Evolucao.Ativo(i, porInvestimento.getOrDefault(i.getId(), List.of()),
					precos == null ? new java.util.TreeMap<LocalDate, BigDecimal>() : precos,
					atuais.get(i.getTipo() + "|" + i.getCodigo()));
		}).toList();
		var cdi = new java.util.TreeMap<LocalDate, BigDecimal>();
		porInvestimento.values()
			.stream()
			.flatMap(List::stream)
			.map(Movimentacao::getData)
			.min(LocalDate::compareTo)
			.ifPresent(de -> cdis.findByDataBetweenOrderByData(de.minusDays(7), hoje)
				.forEach(c -> cdi.put(c.getData(), c.getTaxa())));
		return Evolucao.calcular(periodo, hoje, ativos, cdi);
	}

	public void excluir(UUID usuarioId, UUID id) {
		investimentos.delete(buscar(usuarioId, id));
	}

	@Transactional(readOnly = true)
	public List<FonteAtivos.Cripto> buscarCripto(String texto) {
		var busca = texto == null ? "" : texto.trim();
		if (busca.length() < 2) {
			throw ErroApi.campos(Map.of("busca", "Digite pelo menos 2 letras."));
		}
		return switch (fonte.buscarCripto(busca)) {
			case FonteAtivos.BuscaCripto.Encontradas e -> e.moedas();
			case FonteAtivos.BuscaCripto.Indisponivel x -> throw new ErroApi(HttpStatus.SERVICE_UNAVAILABLE, CRIPTO_FORA);
		};
	}

	private record Ativo(String codigo, String nome, String simbolo, boolean conferido, String exibicao) {
	}

	private Ativo conferirTicker(Map<String, String> campos, String digitado) {
		var ticker = digitado == null ? "" : digitado.trim().toUpperCase(Locale.ROOT);
		var fracionario = FRACIONARIO.matcher(ticker);
		if (fracionario.matches()) {
			ticker = fracionario.group(1);
		}
		if (!TICKER.matcher(ticker).matches()) {
			campos.put("codigo", "Use o código da B3, como PETR4 ou BOVA11.");
			return null;
		}
		return switch (fonte.conferirTicker(ticker)) {
			case FonteAtivos.Conferencia.Existe e -> new Ativo(ticker, e.nome(), null, true, ticker);
			case FonteAtivos.Conferencia.NaoExiste n -> {
				campos.put("codigo", "Não achei " + ticker + " na B3.");
				yield null;
			}
			case FonteAtivos.Conferencia.Indisponivel x -> new Ativo(ticker, null, null, false, ticker);
		};
	}

	private Ativo conferirCripto(Map<String, String> campos, String id) {
		var codigo = id == null ? "" : id.trim();
		if (codigo.isEmpty()) {
			campos.put("codigo", "Escolha a cripto na busca.");
			return null;
		}
		return switch (fonte.conferirCripto(codigo)) {
			case FonteAtivos.Conferencia.Existe e -> new Ativo(codigo, e.nome(), e.simbolo(), true, e.nome());
			case FonteAtivos.Conferencia.NaoExiste n -> {
				campos.put("codigo", "Não achei essa cripto. Escolha na busca.");
				yield null;
			}
			case FonteAtivos.Conferencia.Indisponivel x -> throw new ErroApi(HttpStatus.SERVICE_UNAVAILABLE, CRIPTO_FORA);
		};
	}

	private void repetido(UUID usuarioId, Investimento.Tipo tipo, String codigo, String exibicao) {
		if (investimentos.existsByUsuarioIdAndTipoAndCodigo(usuarioId, tipo, codigo)) {
			throw ErroApi.campos(Map.of("codigo", "Você já tem " + exibicao + ". Use Aportar."));
		}
	}

	private Investimento buscar(UUID usuarioId, UUID id) {
		return investimentos.findByIdAndUsuarioId(id, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, NAO_ENCONTRADO));
	}

	private Detalhe detalhe(Investimento i, List<Movimentacao> movs) {
		return avaliar(List.of(i), Map.of(i.getId(), movs)).getFirst();
	}

	/**
	 * HU-31: junta a posição com a cotação guardada ou o CDI guardado. Nunca chama fonte externa: a tela aparece na
	 * hora com o último valor, e o {@link AtualizadorCotacoes} busca o que está velho quando o app pede.
	 */
	private List<Detalhe> avaliar(List<Investimento> lista, Map<UUID, List<Movimentacao>> porInvestimento) {
		var hoje = LocalDate.now(clock);
		var guardadas = new java.util.HashMap<String, Cotacao>();
		for (var tipo : List.of(Investimento.Tipo.ACAO, Investimento.Tipo.CRIPTO, Investimento.Tipo.TESOURO)) {
			var codigos = lista.stream().filter(i -> i.getTipo() == tipo).map(Investimento::getCodigo).toList();
			if (!codigos.isEmpty()) {
				cotacoes.findByTipoAndCodigoIn(tipo, codigos).forEach(c -> guardadas.put(tipo + "|" + c.getCodigo(), c));
			}
		}
		var rendaFixa = lista.stream().filter(i -> i.getTipo() == Investimento.Tipo.RENDA_FIXA).toList();
		var cdi = new java.util.TreeMap<LocalDate, BigDecimal>();
		var cdiFalhou = false;
		if (!rendaFixa.isEmpty()) {
			rendaFixa.stream()
				.flatMap(i -> porInvestimento.getOrDefault(i.getId(), List.of()).stream())
				.map(Movimentacao::getData)
				.min(LocalDate::compareTo)
				.ifPresent(de -> cdis.findByDataBetweenOrderByData(de, hoje).forEach(c -> cdi.put(c.getData(), c.getTaxa())));
			cdiFalhou = controles.findById(AtualizacaoFonte.CDI).map(AtualizacaoFonte::isFalhou).orElse(false);
		}
		var limite = DiasUteis.anterior(DiasUteis.anterior(hoje));
		var resultado = new ArrayList<Detalhe>();
		for (var i : lista) {
			var ordem = porInvestimento.getOrDefault(i.getId(), List.of())
				.stream()
				.sorted(java.util.Comparator.comparing(Movimentacao::getData).thenComparing(Movimentacao::getCriadoEm))
				.toList();
			var posicao = Posicao.de(i.getTipo(), ordem);
			var inicial = ordem.stream()
				.filter(m -> m.getTipo() == Movimentacao.Tipo.INICIAL)
				.map(Movimentacao::getValor)
				.findFirst()
				.orElse(BigDecimal.ZERO);
			if (i.getTipo() == Investimento.Tipo.RENDA_FIXA) {
				if (posicao.encerrada()) {
					resultado.add(new Detalhe(i, posicao, ordem, ValorAtual.Resultado.VAZIO, null, false, inicial,
							ValorAtual.resultadoEncerrada(ordem)));
					continue;
				}
				var valor = ValorAtual.rendaFixa(i, ordem, cdi);
				// Velho é o CDI guardado, não o da posição: quem venceu num fim de semana para na sexta.
				var ultimoCdi = cdi.isEmpty() ? null : cdi.lastKey();
				var parado = valor.cdiAte() != null && ultimoCdi != null && i.getVencimento().isAfter(ultimoCdi)
						&& (cdiFalhou || ultimoCdi.isBefore(limite));
				resultado.add(new Detalhe(i, posicao, ordem, valor, null, parado, inicial, null));
				continue;
			}
			var cotacao = guardadas.get(i.getTipo() + "|" + i.getCodigo());
			var preco = cotacao == null ? null : cotacao.getPreco();
			var valor = ValorAtual.mercado(posicao, preco);
			var desatualizado = preco != null && !posicao.encerrada() && (cotacao.getFalhouEm() != null
					|| cotacao.getReferencia() == null || cotacao.getReferencia().isBefore(limite));
			resultado.add(new Detalhe(i, posicao, ordem, valor, preco == null ? null : cotacao, desatualizado, inicial,
					null));
		}
		return resultado;
	}

	private static void positivo(Map<String, String> campos, String campo, BigDecimal valor, int maximoCasas,
			BigDecimal limite) {
		if (valor == null || valor.signum() <= 0) {
			campos.put(campo, "Informe um valor maior que zero.");
		}
		else if (valor.compareTo(limite) >= 0) {
			campos.put(campo, "Esse valor é grande demais. Confira os zeros.");
		}
		else if (casas(valor) > maximoCasas) {
			campos.put(campo, maximoCasas == 0 ? "Use um número inteiro." : "Use no máximo " + maximoCasas + " casas.");
		}
	}

	/** Quantidade × preço precisa caber em reais (NUMERIC(12,2)). */
	private static void totalNoLimite(Map<String, String> campos, BigDecimal quantidade, BigDecimal preco) {
		if (quantidade != null && preco != null && !campos.containsKey("quantidade") && !campos.containsKey("preco")
				&& quantidade.multiply(preco).compareTo(LIMITE_REAIS) >= 0) {
			campos.put("preco", "O total passa de R$ 9.999.999.999,99. Confira os zeros.");
		}
	}

	private static int casas(BigDecimal valor) {
		return Math.max(0, valor.stripTrailingZeros().scale());
	}

	private static void falhar(Map<String, String> campos) {
		if (!campos.isEmpty()) {
			throw ErroApi.campos(campos);
		}
	}

}
