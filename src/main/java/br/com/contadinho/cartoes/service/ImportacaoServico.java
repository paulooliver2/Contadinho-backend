package br.com.contadinho.cartoes.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.contadinho.cartoes.ComprasImportadas;
import br.com.contadinho.cartoes.domain.Cartao;
import br.com.contadinho.cartoes.domain.Importacao;
import br.com.contadinho.cartoes.domain.Lancamento;
import br.com.contadinho.cartoes.importacao.ArquivoFatura;
import br.com.contadinho.cartoes.importacao.ArquivoInvalido;
import br.com.contadinho.cartoes.importacao.LeitorFatura;
import br.com.contadinho.cartoes.repository.ImportacaoRepositorio;
import br.com.contadinho.cartoes.repository.LancamentoRepositorio;
import br.com.contadinho.comum.ErroApi;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importação da fatura em PDF ou OFX (HU-06, HU-07, HU-39), com prévia e decisão sobre as compras parecidas com as digitadas
 * (RN-59). Não guarda nada entre a prévia e a confirmação: o app manda o arquivo de novo e o servidor relê.
 */
@Service
@Transactional
public class ImportacaoServico {

	public static final String FATURA_MUDOU = "A fatura mudou desde a prévia. Confira a importação de novo.";

	private final FaturaServico faturas;

	private final LancamentoRepositorio lancamentos;

	private final ImportacaoRepositorio importacoes;

	private final ResponsavelServico responsaveis;

	private final ApplicationEventPublisher eventos;

	private final Clock clock;

	public ImportacaoServico(FaturaServico faturas, LancamentoRepositorio lancamentos, ImportacaoRepositorio importacoes,
			ResponsavelServico responsaveis, ApplicationEventPublisher eventos, Clock clock) {
		this.faturas = faturas;
		this.lancamentos = lancamentos;
		this.importacoes = importacoes;
		this.responsaveis = responsaveis;
		this.eventos = eventos;
		this.clock = clock;
	}

	/** Um lançamento do arquivo na prévia: já importado antes (HU-07) ou novo, talvez parecido com um digitado. */
	public record ItemPrevia(ArquivoFatura.Item item, boolean jaExiste, Lancamento parecida) {
	}

	public record Previa(Cartao.Ciclo ciclo, ArquivoFatura arquivo, List<ItemPrevia> itens) {

		public BigDecimal totalLido() {
			return arquivo.itens().stream().map(ArquivoFatura.Item::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
		}
	}

	public record Resultado(UUID importacaoId, YearMonth mes, int novos, int jaExistiam, int casadas) {
	}

	/** Sem {@code mes}, sugere a fatura pela data do último lançamento e pelo fechamento do cartão (RN-09). */
	@Transactional(readOnly = true)
	public Previa previa(UUID usuarioId, UUID cartaoId, String nomeArquivo, byte[] conteudo, String senha, YearMonth mes) {
		var cartao = faturas.cartao(usuarioId, cartaoId);
		var arquivo = ler(nomeArquivo, conteudo, senha);
		// RN-09: a fatura escolhida na prévia; senão a que o arquivo informa; senão a sugerida pelas datas.
		var destino = mes != null ? mes : arquivo.mes() != null ? arquivo.mes()
				: cartao.mesAberto(arquivo.itens().stream().map(ArquivoFatura.Item::data).max(Comparator.naturalOrder()).orElseThrow());
		return montar(cartao, arquivo, destino);
	}

	/** {@code mesmas}: id externo do arquivo → compra digitada que o usuário confirmou ser a mesma. */
	public Resultado importar(UUID usuarioId, UUID cartaoId, String nomeArquivo, byte[] conteudo, String senha, YearMonth mes,
			Map<String, UUID> mesmas) {
		var cartao = faturas.cartao(usuarioId, cartaoId);
		var previa = montar(cartao, ler(nomeArquivo, conteudo, senha), mes);
		// Só vale casar com a parecida que a prévia mostrou; outra coisa quer dizer que a fatura mudou no meio.
		var parecidas = previa.itens().stream().filter(i -> i.parecida() != null)
			.collect(Collectors.toMap(i -> i.item().idExterno(), i -> i.parecida().getId()));
		mesmas.forEach((idExterno, lancamentoId) -> {
			if (!lancamentoId.equals(parecidas.get(idExterno))) {
				throw new ErroApi(HttpStatus.CONFLICT, FATURA_MUDOU);
			}
		});

		var fatura = faturas.gravarFatura(usuarioId, cartao, mes);
		var importacao = importacoes.save(new Importacao(usuarioId, fatura.getId(), nomeArquivo, previa.arquivo().formato(), previa.arquivo().modelo(),
				previa.arquivo().totalArquivo(), Instant.now(clock)));
		var novas = new ArrayList<UUID>();
		int casadas = 0;
		int jaExistiam = 0;
		for (var i : previa.itens()) {
			if (i.jaExiste()) {
				jaExistiam++;
			}
			else if (mesmas.containsKey(i.item().idExterno())) {
				i.parecida().casar(importacao.getId(), i.item());
				casadas++;
			}
			else {
				// RN-17: a parcela chega com o responsável que o dono escolheu para as próximas.
				novas.add(responsaveis.gravarNova(Lancamento.doArquivo(usuarioId, cartao.getId(), fatura.getId(), importacao.getId(), i.item())).getId());
			}
		}
		// HU-14: as contas fixas pagas neste cartão procuram o lançamento delas.
		if (!novas.isEmpty()) {
			eventos.publishEvent(new ComprasImportadas(usuarioId, cartao.getId(), novas));
		}
		return new Resultado(importacao.getId(), mes, novas.size(), jaExistiam, casadas);
	}

	/** O {@code codigo} diz ao app que precisa pedir a senha do PDF (HU-39). */
	private static ArquivoFatura ler(String nomeArquivo, byte[] conteudo, String senha) {
		try {
			return LeitorFatura.ler(nomeArquivo, conteudo, senha);
		}
		catch (ArquivoInvalido e) {
			var erro = new ErroApi(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
			if (e.codigo() != null) {
				erro.getBody().setProperty("codigo", e.codigo().name());
			}
			throw erro;
		}
	}

	/** RN-11 e RN-59: separa os que já existem e casa cada novo com no máximo uma digitada de mesma data e valor. */
	private Previa montar(Cartao cartao, ArquivoFatura arquivo, YearMonth mes) {
		var ids = arquivo.itens().stream().map(ArquivoFatura.Item::idExterno).toList();
		var existentes = lancamentos.findByCartaoIdAndIdExternoIn(cartao.getId(), ids).stream()
			.map(Lancamento::getIdExterno)
			.collect(Collectors.toSet());
		var digitadas = faturas.gravada(cartao.getId(), mes)
			.map(f -> lancamentos.findByFaturaIdAndOrigemOrderByDataAscDescricao(f.getId(), Lancamento.Origem.MANUAL))
			.orElse(List.of());
		var usadas = new HashSet<UUID>();
		var itens = new ArrayList<ItemPrevia>();
		for (var item : arquivo.itens()) {
			if (existentes.contains(item.idExterno())) {
				itens.add(new ItemPrevia(item, true, null));
				continue;
			}
			var parecida = digitadas.stream()
				.filter(d -> !usadas.contains(d.getId()) && d.getData().equals(item.data())
						&& d.getValor().compareTo(item.valor()) == 0)
				.findFirst()
				.orElse(null);
			if (parecida != null) {
				usadas.add(parecida.getId());
			}
			itens.add(new ItemPrevia(item, false, parecida));
		}
		return new Previa(faturas.ciclo(cartao, mes), arquivo, itens);
	}

}
