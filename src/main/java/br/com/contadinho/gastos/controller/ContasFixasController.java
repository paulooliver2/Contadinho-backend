package br.com.contadinho.gastos.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.comum.UsuarioLogado;
import br.com.contadinho.gastos.domain.ContaFixa;
import br.com.contadinho.gastos.domain.OcorrenciaConta;
import br.com.contadinho.gastos.service.ContaFixaServico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Contas fixas, as ocorrências de cada mês (HU-11) e o pagamento delas (HU-13). */
@RestController
@RequestMapping("/api/contas-fixas")
public class ContasFixasController {

	private final ContaFixaServico servico;

	public ContasFixasController(ContaFixaServico servico) {
		this.servico = servico;
	}

	/** HU-11.1. Com forma CARTAO, o cartão é obrigatório (RN-22); sem "essencial", vale o da categoria (RN-28). */
	public record ContaFixaPedido(
			@NotBlank(message = "Informe a descrição.") @Size(max = 80, message = "Use até 80 caracteres.") String descricao,
			@NotNull(message = "Informe o valor.")
			@DecimalMin(value = "0.01", message = "Informe um valor maior que zero.")
			@Digits(integer = 10, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal valor,
			@NotNull(message = "Escolha a categoria.") UUID categoriaId,
			@NotNull(message = "Informe o dia.")
			@Min(value = 1, message = "Use um dia entre 1 e 31.") @Max(value = 31, message = "Use um dia entre 1 e 31.") Integer diaVencimento,
			@NotNull(message = "Escolha a forma de pagamento.") ContaFixa.FormaPagamento formaPagamento,
			UUID cartaoId,
			Boolean essencial) {

		public ContaFixaPedido {
			descricao = descricao == null ? null : descricao.strip();
		}

		public ContaFixaServico.Dados dados() {
			return new ContaFixaServico.Dados(descricao, valor, categoriaId, diaVencimento, formaPagamento, cartaoId,
					essencial);
		}
	}

	public record ContaFixaResposta(
			@NotNull UUID id,
			@NotNull String descricao,
			@NotNull UUID categoriaId,
			@NotNull Integer diaVencimento,
			@NotNull ContaFixa.FormaPagamento formaPagamento,
			UUID cartaoId,
			Boolean essencial,
			/** HU-11.4: até este dia; a conta não gera mais nos meses seguintes. */
			LocalDate encerradaEm) {

		public static ContaFixaResposta de(ContaFixa c) {
			return new ContaFixaResposta(c.getId(), c.getDescricao(), c.getCategoriaId(), (int) c.getDiaVencimento(),
					c.getFormaPagamento(), c.getCartaoId(), c.getEssencial(), c.getEncerradaEm());
		}
	}

	public record OcorrenciaResposta(
			@NotNull UUID id,
			@NotNull ContaFixaResposta conta,
			/** O valor do cadastro a partir deste mês, para editar a conta (HU-11.3). */
			@NotNull BigDecimal valorAtual,
			@NotNull LocalDate vencimento,
			/** O que conta no mês: o pago, se houver; senão, o previsto (RN-25). */
			@NotNull BigDecimal valor,
			@NotNull BigDecimal valorPrevisto,
			/** Só quando paga (HU-13). */
			BigDecimal valorPago,
			LocalDate pagoEm,
			@NotNull OcorrenciaConta.Status status) {

		public static OcorrenciaResposta de(ContaFixaServico.ContaDoMes c) {
			var o = c.ocorrencia();
			return new OcorrenciaResposta(o.getId(), ContaFixaResposta.de(c.conta()), c.valorAtual(),
					o.getVencimento(), o.valor(), o.getValorPrevisto(), o.getValorPago(), o.getPagoEm(), c.status());
		}
	}

	/** HU-13.1. Sem data, vale hoje; sem valor, o previsto. */
	public record PagamentoContaPedido(
			LocalDate pagoEm,
			@DecimalMin(value = "0.01", message = "Informe um valor maior que zero.")
			@Digits(integer = 10, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal valorPago) {
	}

	public record ContasDoMesResposta(
			@NotNull BigDecimal total,
			@NotNull BigDecimal pago,
			@NotNull BigDecimal aPagar,
			/** Contas pagas no cartão: aparecem aqui, mas entram no mês pela fatura (RN-23). */
			@NotNull BigDecimal noCartao,
			/** Pelo vencimento. */
			@NotNull List<OcorrenciaResposta> contas) {

		public static ContasDoMesResposta de(List<ContaFixaServico.ContaDoMes> lista) {
			var contas = lista.stream().map(OcorrenciaResposta::de).toList();
			return new ContasDoMesResposta(soma(contas, null), soma(contas, OcorrenciaConta.Status.PAGA),
					soma(contas, OcorrenciaConta.Status.A_PAGAR),
					soma(contas, OcorrenciaConta.Status.AGUARDANDO_FATURA)
						.add(soma(contas, OcorrenciaConta.Status.NA_FATURA)),
					contas);
		}

		private static BigDecimal soma(List<OcorrenciaResposta> contas, OcorrenciaConta.Status status) {
			return contas.stream()
				.filter(c -> status == null || c.status() == status)
				.map(OcorrenciaResposta::valor)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		}
	}

	/** HU-11.2: as contas do mês, criando as ocorrências que faltam. */
	@GetMapping("/mes/{mes}")
	public ContasDoMesResposta doMes(@UsuarioLogado UUID usuarioId, @PathVariable YearMonth mes) {
		return ContasDoMesResposta.de(servico.doMes(usuarioId, mes));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ContaFixaResposta cadastrar(@UsuarioLogado UUID usuarioId, @Valid @RequestBody ContaFixaPedido pedido) {
		return ContaFixaResposta.de(servico.cadastrar(usuarioId, pedido.dados()));
	}

	/** HU-11.3: o valor novo vale do mês atual em diante. Conta encerrada é 409. */
	@PutMapping("/{id}")
	public ContaFixaResposta alterar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id,
			@Valid @RequestBody ContaFixaPedido pedido) {
		return ContaFixaResposta.de(servico.alterar(usuarioId, id, pedido.dados()));
	}

	/** HU-13.1. Conta no cartão é 409: ela entra pela fatura (RN-23). Data depois de hoje é 400. */
	@PostMapping("/ocorrencias/{id}/pagamento")
	public OcorrenciaResposta pagar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id,
			@Valid @RequestBody PagamentoContaPedido pedido) {
		return OcorrenciaResposta.de(servico.pagar(usuarioId, id, pedido.pagoEm(), pedido.valorPago()));
	}

	/** HU-13.3: volta a ficar a pagar; débito automático desfeito não é marcado de novo (RN-24). */
	@DeleteMapping("/ocorrencias/{id}/pagamento")
	public OcorrenciaResposta desfazerPagamento(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		return OcorrenciaResposta.de(servico.desfazerPagamento(usuarioId, id));
	}

	/** HU-11.4. */
	@PostMapping("/{id}/encerrar")
	public ContaFixaResposta encerrar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		return ContaFixaResposta.de(servico.encerrar(usuarioId, id));
	}

}
