package br.com.contadinho.receber.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.comum.UsuarioLogado;
import br.com.contadinho.receber.domain.Pagamento;
import br.com.contadinho.receber.service.ReceberServico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A receber: quanto cada pessoa deve, o histórico dela (HU-18) e os pagamentos recebidos (HU-19).
 */
@RestController
@RequestMapping("/api/receber")
public class ReceberController {

	private final ReceberServico servico;

	public ReceberController(ReceberServico servico) {
		this.servico = servico;
	}

	/** RN-29. Saldo = original − recebido. */
	public record SituacaoResposta(
			@NotNull UUID pessoaId,
			@NotNull BigDecimal saldo,
			@NotNull BigDecimal original,
			@NotNull BigDecimal recebido,
			@NotNull ReceberServico.Status status,
			/** Do recebível em aberto que vence primeiro; sem nada em aberto, não vem. */
			LocalDate proximoVencimento) {

		public static SituacaoResposta de(ReceberServico.Situacao s) {
			return new SituacaoResposta(s.pessoaId(), s.saldo(), s.original(), s.recebido(), s.status(),
					s.proximoVencimento());
		}
	}

	public record ResumoResposta(
			@NotNull BigDecimal aReceber,
			@NotNull BigDecimal recebido,
			/** Só quem tem algo registrado, pelo vencimento mais próximo (HU-18.2). */
			@NotNull List<SituacaoResposta> pessoas) {
	}

	public record CompraResposta(
			@NotNull UUID lancamentoId,
			@NotNull LocalDate data,
			@NotNull String descricao,
			@NotNull BigDecimal valor,
			Integer parcelaAtual,
			Integer parcelaTotal,
			/** Apelido e final, como "Itaú •• 3310". */
			@NotNull String cartao,
			/** Mês de vencimento da fatura, como 2026-11. */
			@NotNull String fatura,
			/** O que falta depois de abater os pagamentos, do que vence primeiro (RN-30); zero é paga. */
			@NotNull BigDecimal emAberto) {

		public static CompraResposta de(ReceberServico.CompraDaPessoa d) {
			var c = d.compra();
			return new CompraResposta(c.id(), c.data(), c.descricao(), c.valor(), c.parcelaAtual(), c.parcelaTotal(),
					c.cartao(), c.fatura().toString(), d.emAberto());
		}
	}

	public record PagamentoResposta(@NotNull UUID id, @NotNull BigDecimal valor, @NotNull LocalDate recebidoEm) {

		public static PagamentoResposta de(Pagamento p) {
			return new PagamentoResposta(p.getId(), p.getValor(), p.getRecebidoEm());
		}
	}

	public record ParcelaEmprestimoResposta(
			@NotNull Integer numero,
			@NotNull BigDecimal valor,
			/** Null é "Sem data" (RN-32). */
			LocalDate vencimento,
			/** O que falta da parcela depois do abatimento (RN-30); zero é paga. */
			@NotNull BigDecimal emAberto) {
	}

	public record EmprestimoHistoricoResposta(
			@NotNull UUID id,
			String descricao,
			@NotNull BigDecimal valor,
			@NotNull List<ParcelaEmprestimoResposta> parcelas) {

		public static EmprestimoHistoricoResposta de(ReceberServico.EmprestimoDaPessoa d) {
			var e = d.emprestimo();
			return new EmprestimoHistoricoResposta(e.getId(), e.getDescricao(), e.getValor(), d.parcelas().stream()
				.map(p -> new ParcelaEmprestimoResposta((int) p.numero(), p.valor(), p.vencimento(), p.emAberto()))
				.toList());
		}
	}

	public record HistoricoResposta(
			@NotNull SituacaoResposta situacao,
			@NotNull List<CompraResposta> compras,
			/** Do mais recente para o mais antigo (HU-17). */
			@NotNull List<EmprestimoHistoricoResposta> emprestimos,
			/** Os não desfeitos, do mais recente para o mais antigo. */
			@NotNull List<PagamentoResposta> pagamentos) {
	}

	/** HU-19.1. Sem data, vale hoje. */
	public record PagamentoPedido(
			@NotNull(message = "Informe o valor.")
			@DecimalMin(value = "0.01", message = "Informe um valor maior que zero.")
			@Digits(integer = 10, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal valor,
			LocalDate recebidoEm) {
	}

	@GetMapping
	public ResumoResposta resumo(@UsuarioLogado UUID usuarioId) {
		var resumo = servico.resumo(usuarioId);
		return new ResumoResposta(resumo.aReceber(), resumo.recebido(),
				resumo.pessoas().stream().map(SituacaoResposta::de).toList());
	}

	@GetMapping("/pessoas/{pessoaId}")
	public HistoricoResposta historico(@UsuarioLogado UUID usuarioId, @PathVariable UUID pessoaId) {
		var historico = servico.historico(usuarioId, pessoaId);
		return new HistoricoResposta(SituacaoResposta.de(historico.situacao()),
				historico.compras().stream().map(CompraResposta::de).toList(),
				historico.emprestimos().stream().map(EmprestimoHistoricoResposta::de).toList(),
				historico.pagamentos().stream().map(PagamentoResposta::de).toList());
	}

	/** Maior que o saldo é 400 no campo valor (HU-19.4); sem saldo é 409. */
	@PostMapping("/pessoas/{pessoaId}/pagamentos")
	@ResponseStatus(HttpStatus.CREATED)
	public PagamentoResposta registrar(@UsuarioLogado UUID usuarioId, @PathVariable UUID pessoaId,
			@Valid @RequestBody PagamentoPedido pedido) {
		return PagamentoResposta.de(servico.registrar(usuarioId, pessoaId, pedido.valor(), pedido.recebidoEm()));
	}

	/** HU-19.3. */
	@DeleteMapping("/pagamentos/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void desfazer(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		servico.desfazer(usuarioId, id);
	}

}
