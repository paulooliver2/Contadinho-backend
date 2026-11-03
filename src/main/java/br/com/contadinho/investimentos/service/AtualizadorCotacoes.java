package br.com.contadinho.investimentos.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.contadinho.investimentos.domain.AtualizacaoFonte;
import br.com.contadinho.investimentos.domain.Cotacao;
import br.com.contadinho.investimentos.domain.DiasUteis;
import br.com.contadinho.investimentos.domain.Investimento;
import br.com.contadinho.investimentos.domain.Movimentacao;
import br.com.contadinho.investimentos.domain.Posicao;
import br.com.contadinho.investimentos.repository.AtualizacaoFonteRepositorio;
import br.com.contadinho.investimentos.repository.CdiRepositorio;
import br.com.contadinho.investimentos.repository.CotacaoDiariaRepositorio;
import br.com.contadinho.investimentos.repository.CotacaoRepositorio;
import br.com.contadinho.investimentos.repository.InvestimentoRepositorio;
import br.com.contadinho.investimentos.repository.MovimentacaoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * HU-31: busca o que está velho nas posições abertas do dono, quando o app pede (o servidor grátis dorme; não há
 * tarefa agendada). Cada fonte corre sozinha: uma fora do ar não impede as outras (RN-47). Sem transação em volta das
 * chamadas externas; as gravações são upserts, então dois aparelhos ao mesmo tempo não duplicam nada.
 */
@Service
public class AtualizadorCotacoes {

	private static final Logger log = LoggerFactory.getLogger(AtualizadorCotacoes.class);

	static final Duration INTERVALO = Duration.ofMinutes(30);

	private final InvestimentoRepositorio investimentos;

	private final MovimentacaoRepositorio movimentacoes;

	private final CotacaoRepositorio cotacoes;

	private final CdiRepositorio cdis;

	private final AtualizacaoFonteRepositorio controles;

	private final FonteCotacoes fonte;

	private final CotacaoDiariaRepositorio diarias;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public AtualizadorCotacoes(InvestimentoRepositorio investimentos, MovimentacaoRepositorio movimentacoes,
			CotacaoRepositorio cotacoes, CdiRepositorio cdis, AtualizacaoFonteRepositorio controles, FonteCotacoes fonte,
			CotacaoDiariaRepositorio diarias, JdbcTemplate jdbc, Clock clock) {
		this.diarias = diarias;
		this.investimentos = investimentos;
		this.movimentacoes = movimentacoes;
		this.cotacoes = cotacoes;
		this.cdis = cdis;
		this.controles = controles;
		this.fonte = fonte;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public void atualizar(UUID usuarioId) {
		var agora = Instant.now(clock);
		var hoje = LocalDate.now(clock);
		var lista = investimentos.findByUsuarioIdOrderByCriadoEm(usuarioId);
		var porInvestimento = movimentacoes.findByInvestimentoIdIn(lista.stream().map(Investimento::getId).toList())
			.stream()
			.collect(Collectors.groupingBy(Movimentacao::getInvestimentoId));
		var abertas = lista.stream()
			.filter(i -> !Posicao.de(i.getTipo(), porInvestimento.getOrDefault(i.getId(), List.of())).encerrada())
			.toList();
		// HU-32: a evolução de 12 meses também precisa do passado de quem foi resgatado nesse tempo.
		var umAno = hoje.minusMonths(12);
		var considerados = lista.stream()
			.filter(i -> abertas.contains(i) || porInvestimento.getOrDefault(i.getId(), List.of())
				.stream()
				.anyMatch(m -> !m.getData().isBefore(umAno)))
			.toList();

		mercado(Investimento.Tipo.ACAO, codigos(abertas, Investimento.Tipo.ACAO), fonte::acoes, "brapi", agora, hoje);
		mercado(Investimento.Tipo.CRIPTO, codigos(abertas, Investimento.Tipo.CRIPTO), fonte::criptos, "coingecko", agora,
				hoje);
		var iniciais = new java.util.HashMap<String, LocalDate>();
		considerados.stream()
			.filter(i -> i.getTipo() == Investimento.Tipo.TESOURO)
			.forEach(i -> porInvestimento.getOrDefault(i.getId(), List.of())
				.stream()
				.filter(m -> m.getTipo() == Movimentacao.Tipo.INICIAL)
				.forEach(m -> iniciais.merge(i.getCodigo(), m.getData(), (a, b) -> a.isBefore(b) ? a : b)));
		tesouro(codigos(abertas, Investimento.Tipo.TESOURO), iniciais, agora, hoje);
		considerados.stream()
			.filter(i -> i.getTipo() == Investimento.Tipo.RENDA_FIXA)
			.flatMap(i -> porInvestimento.getOrDefault(i.getId(), List.of()).stream())
			.filter(m -> m.getTipo() == Movimentacao.Tipo.INICIAL)
			.map(Movimentacao::getData)
			.min(LocalDate::compareTo)
			.ifPresent(maisAntiga -> cdi(maisAntiga, agora, hoje));
	}

	private static List<String> codigos(List<Investimento> abertas, Investimento.Tipo tipo) {
		return abertas.stream().filter(i -> i.getTipo() == tipo).map(Investimento::getCodigo).distinct().toList();
	}

	private static boolean recente(Instant quando, Instant agora) {
		return quando != null && quando.isAfter(agora.minus(INTERVALO));
	}

	/** Ação, FII e cripto: velho quando a última tentativa (certa ou não) passou de 30 minutos. */
	private void mercado(Investimento.Tipo tipo, List<String> codigos,
			Function<Collection<String>, Map<String, BigDecimal>> buscar, String nome, Instant agora, LocalDate hoje) {
		if (codigos.isEmpty()) {
			return;
		}
		var guardadas = cotacoes.findByTipoAndCodigoIn(tipo, codigos)
			.stream()
			.collect(Collectors.toMap(Cotacao::getCodigo, c -> c));
		var velhos = codigos.stream().filter(c -> {
			var x = guardadas.get(c);
			return x == null || !(recente(x.getAtualizadoEm(), agora) || recente(x.getFalhouEm(), agora));
		}).toList();
		if (velhos.isEmpty()) {
			return;
		}
		try {
			var precos = buscar.apply(velhos);
			for (var codigo : velhos) {
				var preco = precos.get(codigo);
				if (preco != null) {
					gravarPreco(tipo, codigo, preco, hoje, agora, nome);
				}
				else {
					gravarFalha(tipo, codigo, agora, nome);
				}
			}
		}
		catch (FonteCotacoes.Indisponivel e) {
			log.warn("Cotações de {} não atualizadas: {}", tipo, e.getMessage());
			velhos.forEach(c -> gravarFalha(tipo, c, agora, nome));
		}
	}

	/**
	 * A planilha sai uma vez por dia: velho quando falta o último dia de semana; tenta no máximo a cada 30 min. HU-32:
	 * também baixa quando um título começa antes do que a planilha já cobriu para ele ({@code cotacao.historico_desde}),
	 * e guarda os dias que faltam de cada título desde o último preço do dia guardado.
	 * @param codigos os títulos abertos
	 * @param iniciais a data da posição inicial dos títulos abertos ou com movimentação nos últimos 12 meses
	 */
	private void tesouro(List<String> codigos, Map<String, LocalDate> iniciais, Instant agora, LocalDate hoje) {
		if (iniciais.isEmpty()) {
			return;
		}
		var guardadas = cotacoes.findByTipoAndCodigoIn(Investimento.Tipo.TESOURO, codigos)
			.stream()
			.collect(Collectors.toMap(Cotacao::getCodigo, c -> c));
		// A planilha sai depois do fechamento: o preço de ontem já é o mais novo que existe durante o dia.
		var ultimo = DiasUteis.anterior(hoje);
		var velho = codigos.stream().anyMatch(c -> {
			var x = guardadas.get(c);
			return x == null || x.getReferencia() == null || x.getReferencia().isBefore(ultimo);
		});
		var controle = controles.findById(AtualizacaoFonte.TESOURO);
		var cobertos = new java.util.HashMap<String, LocalDate>();
		cotacoes.findByTipoAndCodigoIn(Investimento.Tipo.TESOURO, iniciais.keySet())
			.stream()
			.filter(c -> c.getHistoricoDesde() != null)
			.forEach(c -> cobertos.put(c.getCodigo(), c.getHistoricoDesde()));
		var semPassado = iniciais.entrySet()
			.stream()
			.filter(e -> cobertos.get(e.getKey()) == null || e.getValue().isBefore(cobertos.get(e.getKey())))
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
		if (!(velho || !semPassado.isEmpty()) || controle.map(x -> recente(x.getTentouEm(), agora)).orElse(false)) {
			return;
		}
		var guardados = diarias.porCodigo(Investimento.Tipo.TESOURO, iniciais.keySet(), hoje);
		var desde = new java.util.HashMap<String, LocalDate>();
		iniciais.forEach((codigo, inicial) -> {
			var dias = guardados.get(codigo);
			var deNovo = dias == null || (semPassado.contains(codigo) && inicial.isBefore(dias.firstKey()));
			desde.put(codigo, deNovo ? inicial : dias.lastKey().plusDays(1));
		});
		try {
			var precos = fonte.tesouro(desde);
			for (var codigo : codigos) {
				var dias = precos.get(codigo);
				if (dias != null && !dias.isEmpty()) {
					var maisNovo = dias.getLast();
					gravarPreco(Investimento.Tipo.TESOURO, codigo, maisNovo.valor(), maisNovo.referencia(), agora,
							"tesouro");
				}
				else {
					gravarFalha(Investimento.Tipo.TESOURO, codigo, agora, "tesouro");
				}
			}
			gravarDiarios(precos);
			// Mesmo o título que a planilha não tem fica coberto: não baixa de novo a cada 30 minutos por ele.
			iniciais.forEach((codigo, inicial) -> gravarCobertura(codigo, inicial));
			gravarControle(AtualizacaoFonte.TESOURO, agora, false, null);
		}
		catch (FonteCotacoes.Indisponivel e) {
			log.warn("Preços do Tesouro não atualizados: {}", e.getMessage());
			codigos.forEach(c -> gravarFalha(Investimento.Tipo.TESOURO, c, agora, "tesouro"));
			gravarControle(AtualizacaoFonte.TESOURO, agora, true, null);
		}
	}

	/**
	 * O CDI até o dia de semana anterior a hoje (o de hoje só sai amanhã), e para trás quando aparece uma aplicação
	 * mais antiga que o primeiro dia coberto. Para frente, tenta no máximo a cada 30 minutos.
	 */
	private void cdi(LocalDate maisAntiga, Instant agora, LocalDate hoje) {
		var ontem = hoje.minusDays(1);
		if (maisAntiga.isAfter(ontem)) {
			return;
		}
		var controle = controles.findById(AtualizacaoFonte.CDI);
		var cobreDesde = controle.map(AtualizacaoFonte::getCobreDesde).orElse(null);
		var ultimoGuardado = cdis.findTopByOrderByDataDesc().map(c -> c.getData()).orElse(null);
		var paraTras = cobreDesde == null || maisAntiga.isBefore(cobreDesde);
		var paraFrente = ultimoGuardado == null || ultimoGuardado.isBefore(DiasUteis.anterior(hoje));
		var esperar = controle.map(x -> recente(x.getTentouEm(), agora)).orElse(false);
		if (!paraTras && (!paraFrente || esperar)) {
			return;
		}
		try {
			if (cobreDesde == null) {
				gravarCdi(fonte.cdi(maisAntiga, ontem));
			}
			else {
				if (paraTras) {
					gravarCdi(fonte.cdi(maisAntiga, cobreDesde.minusDays(1)));
				}
				if (paraFrente && !esperar) {
					var de = ultimoGuardado == null ? cobreDesde : ultimoGuardado.plusDays(1);
					if (!de.isAfter(ontem)) {
						gravarCdi(fonte.cdi(de, ontem));
					}
				}
			}
			gravarControle(AtualizacaoFonte.CDI, agora, false, paraTras ? maisAntiga : null);
		}
		catch (FonteCotacoes.Indisponivel e) {
			log.warn("CDI não atualizado: {}", e.getMessage());
			gravarControle(AtualizacaoFonte.CDI, agora, true, null);
		}
	}

	private void gravarPreco(Investimento.Tipo tipo, String codigo, BigDecimal preco, LocalDate referencia,
			Instant agora, String nome) {
		jdbc.update("""
				INSERT INTO cotacao (id, tipo, codigo, preco, referencia, atualizado_em, falhou_em, fonte)
				VALUES (?, ?, ?, ?, ?, ?, NULL, ?)
				ON CONFLICT (tipo, codigo) DO UPDATE SET preco = EXCLUDED.preco, referencia = EXCLUDED.referencia,
				    atualizado_em = EXCLUDED.atualizado_em, falhou_em = NULL, fonte = EXCLUDED.fonte
				""", UUID.randomUUID(), tipo.name(), codigo, preco, Date.valueOf(referencia), Timestamp.from(agora),
				nome);
		// HU-32: o mesmo preço vira o preço daquele dia; o último do dia vence.
		jdbc.update("""
				INSERT INTO cotacao_diaria (tipo, codigo, data, preco) VALUES (?, ?, ?, ?)
				ON CONFLICT (tipo, codigo, data) DO UPDATE SET preco = EXCLUDED.preco
				""", tipo.name(), codigo, Date.valueOf(referencia), preco);
	}

	/** LEAST ignora o nulo: a cobertura do título só cresce para trás. */
	private void gravarCobertura(String codigo, LocalDate desde) {
		jdbc.update("""
				INSERT INTO cotacao (id, tipo, codigo, fonte, historico_desde) VALUES (?, 'TESOURO', ?, 'tesouro', ?)
				ON CONFLICT (tipo, codigo) DO UPDATE
				    SET historico_desde = LEAST(cotacao.historico_desde, EXCLUDED.historico_desde)
				""", UUID.randomUUID(), codigo, Date.valueOf(desde));
	}

	private void gravarDiarios(Map<String, List<FonteCotacoes.Preco>> precos) {
		var linhas = precos.entrySet()
			.stream()
			.flatMap(e -> e.getValue()
				.stream()
				.map(p -> new Object[] { Investimento.Tipo.TESOURO.name(), e.getKey(), Date.valueOf(p.referencia()),
						p.valor() }))
			.toList();
		if (linhas.isEmpty()) {
			return;
		}
		jdbc.batchUpdate("""
				INSERT INTO cotacao_diaria (tipo, codigo, data, preco) VALUES (?, ?, ?, ?)
				ON CONFLICT (tipo, codigo, data) DO UPDATE SET preco = EXCLUDED.preco
				""", linhas);
	}

	/** Mantém o último preço e marca a falha (RN-47). */
	private void gravarFalha(Investimento.Tipo tipo, String codigo, Instant agora, String nome) {
		jdbc.update("""
				INSERT INTO cotacao (id, tipo, codigo, falhou_em, fonte) VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (tipo, codigo) DO UPDATE SET falhou_em = EXCLUDED.falhou_em
				""", UUID.randomUUID(), tipo.name(), codigo, Timestamp.from(agora), nome);
	}

	private void gravarCdi(Map<LocalDate, BigDecimal> dias) {
		if (dias.isEmpty()) {
			return;
		}
		var linhas = dias.entrySet().stream().map(e -> new Object[] { Date.valueOf(e.getKey()), e.getValue() }).toList();
		jdbc.batchUpdate("INSERT INTO cdi_diario (data, taxa) VALUES (?, ?) ON CONFLICT (data) DO NOTHING", linhas);
	}

	/** LEAST ignora o nulo: a cobertura só cresce para trás. */
	private void gravarControle(String nome, Instant agora, boolean falhou, LocalDate cobreDesde) {
		jdbc.update("""
				INSERT INTO atualizacao_fonte (fonte, tentou_em, falhou, cobre_desde) VALUES (?, ?, ?, ?)
				ON CONFLICT (fonte) DO UPDATE SET tentou_em = EXCLUDED.tentou_em, falhou = EXCLUDED.falhou,
				    cobre_desde = LEAST(atualizacao_fonte.cobre_desde, EXCLUDED.cobre_desde)
				""", nome, Timestamp.from(agora), falhou, cobreDesde == null ? null : Date.valueOf(cobreDesde));
	}

}
