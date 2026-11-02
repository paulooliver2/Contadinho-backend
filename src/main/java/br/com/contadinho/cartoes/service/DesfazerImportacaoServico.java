package br.com.contadinho.cartoes.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import br.com.contadinho.cartoes.PagoDasCompras;
import br.com.contadinho.cartoes.domain.Importacao;
import br.com.contadinho.cartoes.domain.Lancamento;
import br.com.contadinho.cartoes.repository.FaturaRepositorio;
import br.com.contadinho.cartoes.repository.ImportacaoRepositorio;
import br.com.contadinho.cartoes.repository.LancamentoRepositorio;
import br.com.contadinho.comum.ErroApi;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-44: desfazer uma importação. As compras do arquivo saem, e com elas o que as pessoas deviam por elas (o banco
 * apaga os recebíveis e solta as contas fixas e os gastos ligados, que voltam a aguardar a fatura). A compra digitada
 * que a importação casou volta a ser digitada. Os pagamentos ficam: abatem o resto ou viram crédito (RN-16, RN-30).
 */
@Service
public class DesfazerImportacaoServico {

	static final String NAO_ENCONTRADA = "Importação não encontrada.";

	private final ImportacaoRepositorio importacoes;

	private final LancamentoRepositorio lancamentos;

	private final FaturaRepositorio faturas;

	private final List<PagoDasCompras> pagos;

	public DesfazerImportacaoServico(ImportacaoRepositorio importacoes, LancamentoRepositorio lancamentos,
			FaturaRepositorio faturas, List<PagoDasCompras> pagos) {
		this.importacoes = importacoes;
		this.lancamentos = lancamentos;
		this.faturas = faturas;
		this.pagos = pagos;
	}

	/**
	 * @param removidas compras do arquivo que saem
	 * @param voltamADigitadas compras digitadas que a importação casou
	 * @param pagoPorPessoa o que cada pessoa já pagou das compras que saem (HU-44.2)
	 */
	public record Impacto(Importacao importacao, YearMonth mes, int removidas, int voltamADigitadas,
			Map<UUID, BigDecimal> pagoPorPessoa) {
	}

	@Transactional(readOnly = true)
	public Impacto impacto(UUID usuarioId, UUID importacaoId) {
		var importacao = daPessoa(usuarioId, importacaoId);
		var itens = lancamentos.findByImportacaoId(importacaoId);
		var saem = itens.stream().filter(l -> !l.isDigitadoAntes()).map(Lancamento::getId).toList();
		var pago = new TreeMap<UUID, BigDecimal>();
		pagos.forEach(p -> p.pago(usuarioId, saem).forEach((pessoa, valor) -> pago.merge(pessoa, valor, BigDecimal::add)));
		var mes = faturas.findById(importacao.getFaturaId()).map(f -> YearMonth.from(f.getVencimento())).orElseThrow();
		return new Impacto(importacao, mes, saem.size(), itens.size() - saem.size(), pago);
	}

	@Transactional
	public Impacto desfazer(UUID usuarioId, UUID importacaoId) {
		var impacto = impacto(usuarioId, importacaoId);
		var faturaId = impacto.importacao().getFaturaId();
		var saem = new ArrayList<Lancamento>();
		for (var l : lancamentos.findByImportacaoId(importacaoId)) {
			if (l.isDigitadoAntes()) {
				l.descasar();
			}
			else {
				saem.add(l);
			}
		}
		// A casada solta a importação antes de ela sair: apagar a importação apaga em cascata o que ainda aponta para ela.
		lancamentos.flush();
		lancamentos.deleteAll(saem);
		importacoes.delete(impacto.importacao());
		importacoes.flush();
		// Sem nada, a fatura deixa de existir, e as datas dela voltam a seguir os dias do cartão.
		if (!lancamentos.existsByFaturaId(faturaId) && !importacoes.existsByFaturaId(faturaId)) {
			faturas.deleteById(faturaId);
		}
		return impacto;
	}

	private Importacao daPessoa(UUID usuarioId, UUID importacaoId) {
		return importacoes.findByIdAndUsuarioId(importacaoId, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, NAO_ENCONTRADA));
	}

}
