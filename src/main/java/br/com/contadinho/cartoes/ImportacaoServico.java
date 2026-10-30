package br.com.contadinho.cartoes;

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

import br.com.contadinho.comum.ErroApi;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importação de fatura em OFX (HU-06, HU-07), com prévia e decisão sobre as compras parecidas com as digitadas
 * (RN-59). Não guarda nada entre a prévia e a confirmação: o app manda o arquivo de novo e o servidor relê.
 */
@Service
@Transactional
class ImportacaoServico {

	static final String FATURA_MUDOU = "A fatura mudou desde a prévia. Confira a importação de novo.";

	private static final String MODELO = "OFX_GENERICO";

	private final FaturaServico faturas;

	private final LancamentoRepositorio lancamentos;

	private final ImportacaoRepositorio importacoes;

	private final Clock clock;

	ImportacaoServico(FaturaServico faturas, LancamentoRepositorio lancamentos, ImportacaoRepositorio importacoes,
			Clock clock) {
		this.faturas = faturas;
		this.lancamentos = lancamentos;
		this.importacoes = importacoes;
		this.clock = clock;
	}

	/** Um lançamento do arquivo na prévia: já importado antes (HU-07) ou novo, talvez parecido com um digitado. */
	record ItemPrevia(LeitorOfx.Item item, boolean jaExiste, Lancamento parecida) {
	}

	record Previa(Cartao.Ciclo ciclo, LeitorOfx.Arquivo arquivo, List<ItemPrevia> itens) {

		BigDecimal totalLido() {
			return arquivo.itens().stream().map(LeitorOfx.Item::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
		}
	}

	record Resultado(UUID importacaoId, YearMonth mes, int novos, int jaExistiam, int casadas) {
	}

	/** Sem {@code mes}, sugere a fatura pela data do último lançamento e pelo fechamento do cartão (RN-09). */
	@Transactional(readOnly = true)
	Previa previa(UUID usuarioId, UUID cartaoId, String nomeArquivo, byte[] conteudo, YearMonth mes) {
		var cartao = faturas.cartao(usuarioId, cartaoId);
		var arquivo = ler(nomeArquivo, conteudo);
		var destino = mes != null ? mes
				: cartao.mesAberto(arquivo.itens().stream().map(LeitorOfx.Item::data).max(Comparator.naturalOrder()).orElseThrow());
		return montar(cartao, arquivo, destino);
	}

	/** {@code mesmas}: id externo do arquivo → compra digitada que o usuário confirmou ser a mesma. */
	Resultado importar(UUID usuarioId, UUID cartaoId, String nomeArquivo, byte[] conteudo, YearMonth mes,
			Map<String, UUID> mesmas) {
		var cartao = faturas.cartao(usuarioId, cartaoId);
		var previa = montar(cartao, ler(nomeArquivo, conteudo), mes);
		// Só vale casar com a parecida que a prévia mostrou; outra coisa quer dizer que a fatura mudou no meio.
		var parecidas = previa.itens().stream().filter(i -> i.parecida() != null)
			.collect(Collectors.toMap(i -> i.item().idExterno(), i -> i.parecida().getId()));
		mesmas.forEach((idExterno, lancamentoId) -> {
			if (!lancamentoId.equals(parecidas.get(idExterno))) {
				throw new ErroApi(HttpStatus.CONFLICT, FATURA_MUDOU);
			}
		});

		var fatura = faturas.gravarFatura(usuarioId, cartao, mes);
		var importacao = importacoes.save(new Importacao(usuarioId, fatura.getId(), nomeArquivo, "OFX", MODELO,
				previa.arquivo().totalArquivo(), Instant.now(clock)));
		int novos = 0;
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
				lancamentos.save(Lancamento.doArquivo(usuarioId, cartao.getId(), fatura.getId(), importacao.getId(), i.item()));
				novos++;
			}
		}
		return new Resultado(importacao.getId(), mes, novos, jaExistiam, casadas);
	}

	private static LeitorOfx.Arquivo ler(String nomeArquivo, byte[] conteudo) {
		try {
			return LeitorOfx.ler(nomeArquivo, conteudo);
		}
		catch (LeitorOfx.ArquivoInvalido e) {
			throw new ErroApi(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
		}
	}

	/** RN-11 e RN-59: separa os que já existem e casa cada novo com no máximo uma digitada de mesma data e valor. */
	private Previa montar(Cartao cartao, LeitorOfx.Arquivo arquivo, YearMonth mes) {
		var ids = arquivo.itens().stream().map(LeitorOfx.Item::idExterno).toList();
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
