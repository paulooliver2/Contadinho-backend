package br.com.contadinho.investimentos.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.comum.UsuarioLogado;
import br.com.contadinho.investimentos.domain.Investimento;
import br.com.contadinho.investimentos.domain.Movimentacao;
import br.com.contadinho.investimentos.service.AtualizadorCotacoes;
import br.com.contadinho.investimentos.service.FonteAtivos;
import br.com.contadinho.investimentos.service.InvestimentoServico;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A carteira de investimentos (HU-30): ações, cripto, Tesouro e renda fixa, com a posição de hoje e as
 * movimentações seguintes.
 */
@RestController
@RequestMapping("/api/investimentos")
public class InvestimentosController {

	private final InvestimentoServico servico;

	private final AtualizadorCotacoes atualizador;

	public InvestimentosController(InvestimentoServico servico, AtualizadorCotacoes atualizador) {
		this.servico = servico;
		this.atualizador = atualizador;
	}

	/**
	 * Os campos valem conforme o tipo. Ação e cripto: codigo, quantidade e preco. Tesouro: tesouroTipo, tesouroAno,
	 * quantidade e valor. Renda fixa: produto, emissor, valor, dataAplicacao, indexador, taxa e vencimento.
	 */
	public record NovoInvestimento(Investimento.Tipo tipo,
			/** Ticker da B3 (ação/FII/ETF) ou o id da cripto que veio da busca. */
			String codigo, BigDecimal quantidade, BigDecimal preco, BigDecimal valor,
			InvestimentoServico.TituloTesouro tesouroTipo, Integer tesouroAno, Investimento.Produto produto,
			String emissor, Investimento.Indexador indexador,
			/** % do CDI ou % ao ano, conforme o indexador. */
			BigDecimal taxa, LocalDate dataAplicacao, LocalDate vencimento) {

		InvestimentoServico.Novo novo() {
			return new InvestimentoServico.Novo(tipo, codigo, quantidade, preco, valor, tesouroTipo, tesouroAno,
					produto, emissor, indexador, taxa, dataAplicacao, vencimento);
		}
	}

	/** Sem data, é hoje. Renda fixa só resgata, por valor; {@code total} encerra a aplicação. */
	public record NovaMovimentacao(Movimentacao.Tipo tipo, LocalDate data, BigDecimal quantidade, BigDecimal preco,
			BigDecimal valor, Boolean total) {

		InvestimentoServico.NovaMovimentacao nova() {
			return new InvestimentoServico.NovaMovimentacao(tipo, data, quantidade, preco, valor, total);
		}
	}

	public record MovimentacaoResposta(@NotNull UUID id, @NotNull Movimentacao.Tipo tipo, @NotNull LocalDate data,
			BigDecimal quantidade, BigDecimal preco,
			/** Vazio no resgate por quantidade (ação, cripto, Tesouro). */
			BigDecimal valor, @NotNull Boolean total) {

		static MovimentacaoResposta de(Movimentacao m) {
			return new MovimentacaoResposta(m.getId(), m.getTipo(), m.getData(), m.getQuantidade(), m.getPreco(),
					m.getValor(), m.isTotal());
		}
	}

	/**
	 * Quantidade e preço médio ficam vazios na renda fixa. HU-31: o valor de hoje e o ganho ficam vazios sem cotação
	 * (ou sem CDI depois da aplicação) e nas encerradas; {@code resultado} só na renda fixa encerrada.
	 */
	public record PosicaoResumo(@NotNull UUID id, @NotNull Investimento.Tipo tipo, @NotNull String codigo,
			String nome, String simbolo, Investimento.Produto produto, Investimento.Indexador indexador,
			BigDecimal taxa, LocalDate vencimento, @NotNull Boolean conferido, BigDecimal quantidade,
			BigDecimal precoMedio, @NotNull BigDecimal aplicado, @NotNull Boolean encerrada,
			/** O valor da aplicação inicial (na renda fixa encerrada, o aplicado vira 0). */
			@NotNull BigDecimal aplicadoInicial, BigDecimal valorAtual, BigDecimal ganho, BigDecimal ganhoPercentual,
			/** O preço usado: a cotação da ação ou da cripto, o preço de venda do título. */
			BigDecimal cotacao,
			/** Quando o servidor buscou a cotação com sucesso. */
			Instant atualizadoEm,
			/** Renda fixa: o último dia útil corrigido. */
			LocalDate cdiAte,
			/** A última busca falhou, ou o valor tem mais de 1 dia útil (RN-47). */
			@NotNull Boolean desatualizado,
			/** Renda fixa encerrada: o que voltou menos o que foi aplicado. */
			BigDecimal resultado) {

		static PosicaoResumo de(InvestimentoServico.Detalhe d) {
			var i = d.investimento();
			var p = d.posicao();
			var v = d.valor();
			var c = d.cotacao();
			return new PosicaoResumo(i.getId(), i.getTipo(), i.getCodigo(), i.getNome(), i.getSimbolo(),
					i.getProduto(), i.getIndexador(), i.getTaxa(), i.getVencimento(), i.isConferido(), p.quantidade(),
					p.precoMedio(), p.aplicado(), p.encerrada(), d.aplicadoInicial(), v.valorAtual(), v.ganho(),
					v.ganhoPercentual(), c == null ? null : c.getPreco(), c == null ? null : c.getAtualizadoEm(),
					v.cdiAte(), d.desatualizado(), d.resultado());
		}
	}

	/** A posição e as movimentações, da mais antiga para a mais nova; a primeira é a INICIAL. */
	public record PosicaoDetalhe(@NotNull UUID id, @NotNull Investimento.Tipo tipo, @NotNull String codigo,
			String nome, String simbolo, Investimento.Produto produto, Investimento.Indexador indexador,
			BigDecimal taxa, LocalDate vencimento, @NotNull Boolean conferido, BigDecimal quantidade,
			BigDecimal precoMedio, @NotNull BigDecimal aplicado, @NotNull Boolean encerrada,
			@NotNull BigDecimal aplicadoInicial, BigDecimal valorAtual, BigDecimal ganho, BigDecimal ganhoPercentual,
			BigDecimal cotacao, Instant atualizadoEm, LocalDate cdiAte, @NotNull Boolean desatualizado,
			BigDecimal resultado, @NotNull List<MovimentacaoResposta> movimentacoes) {

		static PosicaoDetalhe de(InvestimentoServico.Detalhe d) {
			var r = PosicaoResumo.de(d);
			return new PosicaoDetalhe(r.id(), r.tipo(), r.codigo(), r.nome(), r.simbolo(), r.produto(), r.indexador(),
					r.taxa(), r.vencimento(), r.conferido(), r.quantidade(), r.precoMedio(), r.aplicado(),
					r.encerrada(), r.aplicadoInicial(), r.valorAtual(), r.ganho(), r.ganhoPercentual(), r.cotacao(),
					r.atualizadoEm(), r.cdiAte(), r.desatualizado(), r.resultado(),
					d.movimentacoes().stream().map(MovimentacaoResposta::de).toList());
		}
	}

	/**
	 * O total aplicado soma só as posições abertas. HU-31: patrimônio e ganho somam as abertas com valor;
	 * {@code semCotacao} conta as abertas que ficaram de fora.
	 */
	public record Carteira(@NotNull BigDecimal totalAplicado, @NotNull BigDecimal patrimonio,
			@NotNull BigDecimal ganho, BigDecimal ganhoPercentual, @NotNull Integer semCotacao,
			@NotNull List<PosicaoResumo> posicoes) {

		static Carteira de(List<PosicaoResumo> posicoes) {
			var abertas = posicoes.stream().filter(p -> !p.encerrada()).toList();
			var total = abertas.stream().map(PosicaoResumo::aplicado).reduce(BigDecimal.ZERO, BigDecimal::add);
			var comValor = abertas.stream().filter(p -> p.valorAtual() != null).toList();
			var patrimonio = comValor.stream().map(PosicaoResumo::valorAtual).reduce(BigDecimal.ZERO, BigDecimal::add);
			var aplicadoComValor = comValor.stream().map(PosicaoResumo::aplicado).reduce(BigDecimal.ZERO, BigDecimal::add);
			var ganho = patrimonio.subtract(aplicadoComValor);
			var percentual = aplicadoComValor.signum() == 0 ? null
					: ganho.multiply(BigDecimal.valueOf(100)).divide(aplicadoComValor, 2, RoundingMode.HALF_UP);
			return new Carteira(total, patrimonio, ganho, percentual, abertas.size() - comValor.size(), posicoes);
		}
	}

	public record CriptoEncontrada(@NotNull String id, @NotNull String simbolo, @NotNull String nome) {

		static CriptoEncontrada de(FonteAtivos.Cripto c) {
			return new CriptoEncontrada(c.id(), c.simbolo(), c.nome());
		}
	}

	/** Responde na hora com o que está guardado; nunca chama fonte externa. */
	@GetMapping
	public Carteira carteira(@UsuarioLogado UUID usuarioId) {
		return Carteira.de(servico.carteira(usuarioId).stream().map(PosicaoResumo::de).toList());
	}

	/**
	 * HU-31: busca o que está velho nas posições abertas do dono e devolve a carteira. Uma fonte fora não faz o
	 * pedido falhar: os ativos dela voltam com {@code desatualizado}.
	 */
	@PostMapping("/atualizar")
	public Carteira atualizar(@UsuarioLogado UUID usuarioId) {
		atualizador.atualizar(usuarioId);
		return carteira(usuarioId);
	}

	@GetMapping("/{id}")
	public PosicaoDetalhe detalhe(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		return PosicaoDetalhe.de(servico.detalhe(usuarioId, id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PosicaoDetalhe cadastrar(@UsuarioLogado UUID usuarioId, @RequestBody NovoInvestimento pedido) {
		return PosicaoDetalhe.de(servico.cadastrar(usuarioId, pedido.novo()));
	}

	@PostMapping("/{id}/movimentacoes")
	@ResponseStatus(HttpStatus.CREATED)
	public PosicaoDetalhe movimentar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id,
			@RequestBody NovaMovimentacao pedido) {
		return PosicaoDetalhe.de(servico.movimentar(usuarioId, id, pedido.nova()));
	}

	/** A INICIAL não se exclui; nem a que deixaria um passo negativo (400, nada muda). */
	@DeleteMapping("/{id}/movimentacoes/{movimentacaoId}")
	public PosicaoDetalhe excluirMovimentacao(@UsuarioLogado UUID usuarioId, @PathVariable UUID id,
			@PathVariable UUID movimentacaoId) {
		return PosicaoDetalhe.de(servico.excluirMovimentacao(usuarioId, id, movimentacaoId));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void excluir(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		servico.excluir(usuarioId, id);
	}

	/** Pelo menos 2 letras; 503 quando o CoinGecko está fora do ar. */
	@GetMapping("/cripto")
	public List<CriptoEncontrada> buscarCripto(@RequestParam(required = false) String busca) {
		return servico.buscarCripto(busca).stream().map(CriptoEncontrada::de).toList();
	}

}
