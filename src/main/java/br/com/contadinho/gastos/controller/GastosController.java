package br.com.contadinho.gastos.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.comum.UsuarioLogado;
import br.com.contadinho.gastos.domain.Gasto;
import br.com.contadinho.gastos.service.GastoServico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

/** Gastos avulsos (HU-12) e a correção deles (HU-46). */
@RestController
@RequestMapping("/api/gastos")
public class GastosController {

	private final GastoServico servico;

	public GastosController(GastoServico servico) {
		this.servico = servico;
	}

	/** HU-12.1. Sem data, vale hoje; sem "essencial", vale o da categoria (RN-28). */
	public record GastoPedido(
			@NotBlank(message = "Informe a descrição.") @Size(max = 80, message = "Use até 80 caracteres.") String descricao,
			@NotNull(message = "Informe o valor.")
			@DecimalMin(value = "0.01", message = "Informe um valor maior que zero.")
			@Digits(integer = 10, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal valor,
			LocalDate data,
			@NotNull(message = "Escolha a categoria.") UUID categoriaId,
			@NotNull(message = "Escolha como pagou.") Gasto.FormaPagamento formaPagamento,
			UUID cartaoId,
			Boolean essencial) {

		public GastoPedido {
			descricao = descricao == null ? null : descricao.strip();
		}

		GastoServico.Dados dados() {
			return new GastoServico.Dados(descricao, valor, data, categoriaId, formaPagamento, cartaoId, essencial);
		}
	}

	public record GastoResposta(
			@NotNull UUID id,
			@NotNull String descricao,
			/** O que foi registrado. */
			@NotNull BigDecimal valor,
			@NotNull LocalDate data,
			@NotNull UUID categoriaId,
			@NotNull Gasto.FormaPagamento formaPagamento,
			UUID cartaoId,
			Boolean essencial,
			@NotNull Gasto.Status status,
			/** O lançamento da fatura que o paga, quando "na fatura" (RN-26). */
			CompraDaFatura compra) {

		public static GastoResposta de(GastoServico.GastoDoMes g) {
			var gasto = g.gasto();
			return new GastoResposta(gasto.getId(), gasto.getDescricao(), gasto.getValor(), gasto.getData(),
					gasto.getCategoriaId(), gasto.getFormaPagamento(), gasto.getCartaoId(), gasto.getEssencial(),
					gasto.status(), g.compra() == null ? null : CompraDaFatura.de(g.compra()));
		}
	}

	public record GastosDoMesResposta(
			/** Os pagos fora do cartão: os do cartão entram pela fatura (RN-26). */
			@NotNull BigDecimal total,
			@NotNull BigDecimal noCartao,
			/** Pela data, do mais recente para o mais antigo. */
			@NotNull List<GastoResposta> gastos) {

		public static GastosDoMesResposta de(List<GastoServico.GastoDoMes> lista) {
			var total = BigDecimal.ZERO;
			var noCartao = BigDecimal.ZERO;
			for (var g : lista) {
				if (g.gasto().isNoCartao()) {
					noCartao = noCartao.add(g.valor());
				}
				else {
					total = total.add(g.valor());
				}
			}
			return new GastosDoMesResposta(total, noCartao, lista.stream().map(GastoResposta::de).toList());
		}
	}

	/** Os gastos avulsos do mês (yyyy-MM), pela data. */
	@GetMapping("/mes/{mes}")
	public GastosDoMesResposta doMes(@UsuarioLogado UUID usuarioId, @PathVariable YearMonth mes) {
		return GastosDoMesResposta.de(servico.doMes(usuarioId, mes));
	}

	/** HU-12. Data depois de hoje é 400. No cartão, já sai ligado se a fatura com a compra chegou (RN-26). */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GastoResposta cadastrar(@UsuarioLogado UUID usuarioId, @Valid @RequestBody GastoPedido pedido) {
		return GastoResposta.de(servico.cadastrar(usuarioId, pedido.dados()));
	}

	/** HU-46.1. */
	@PutMapping("/{id}")
	public GastoResposta alterar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id,
			@Valid @RequestBody GastoPedido pedido) {
		return GastoResposta.de(servico.alterar(usuarioId, id, pedido.dados()));
	}

	/** HU-46.1 e HU-46.3: o lançamento da fatura não muda. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void excluir(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		servico.excluir(usuarioId, id);
	}

}
