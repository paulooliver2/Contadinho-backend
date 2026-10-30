package br.com.contadinho.cartoes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.comum.UsuarioLogado;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A fatura de cada mês e as compras digitadas nela (HU-47). O mês é o do vencimento, como 2026-11 (RN-58).
 */
@RestController
class FaturasController {

	private final FaturaServico servico;

	FaturasController(FaturaServico servico) {
		this.servico = servico;
	}

	/** RN-60. Parcela é opcional, mas vem com o total (3 de 10). */
	record CompraPedido(
			@NotNull(message = "Informe a data.") LocalDate data,
			@NotBlank(message = "Informe a descrição.") @Size(max = 255, message = "Use até 255 caracteres.") String descricao,
			@NotNull(message = "Informe o valor.")
			@Digits(integer = 10, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal valor,
			@Min(value = 1, message = "Use um número entre 1 e 99.") @Max(value = 99, message = "Use um número entre 1 e 99.") Integer parcelaAtual,
			@Min(value = 1, message = "Use um número entre 1 e 99.") @Max(value = 99, message = "Use um número entre 1 e 99.") Integer parcelaTotal) {

		CompraPedido {
			descricao = descricao == null ? null : descricao.strip();
		}

		Lancamento.Compra compra() {
			return new Lancamento.Compra(data, descricao, valor, parcelaAtual, parcelaTotal);
		}
	}

	record LancamentoResposta(
			@NotNull UUID id,
			@NotNull LocalDate data,
			@NotNull String descricao,
			@NotNull BigDecimal valor,
			Integer parcelaAtual,
			Integer parcelaTotal,
			/** Final do cartão que fez a compra, quando o arquivo traz (RN-19). */
			String finalCartao,
			@NotNull Lancamento.Origem origem) {

		static LancamentoResposta de(Lancamento l) {
			return new LancamentoResposta(l.getId(), l.getData(), l.getDescricao(), l.getValor(), l.getParcelaAtual(),
					l.getParcelaTotal(), l.getFinalCartao(), l.getOrigem());
		}
	}

	record FaturaResposta(
			@NotNull String mes,
			@NotNull LocalDate fechamento,
			@NotNull LocalDate vencimento,
			@NotNull Boolean aberta,
			@NotNull BigDecimal total,
			@NotNull List<LancamentoResposta> lancamentos) {
	}

	@GetMapping("/api/cartoes/{cartaoId}/faturas/{mes}")
	FaturaResposta ver(@UsuarioLogado UUID usuarioId, @PathVariable UUID cartaoId, @PathVariable YearMonth mes) {
		var vista = servico.ver(usuarioId, cartaoId, mes);
		var ciclo = vista.ciclo();
		return new FaturaResposta(ciclo.mes().toString(), ciclo.fechamento(), ciclo.vencimento(), vista.aberta(),
				vista.total(), vista.lancamentos().stream().map(LancamentoResposta::de).toList());
	}

	@PostMapping("/api/cartoes/{cartaoId}/faturas/{mes}/lancamentos")
	@ResponseStatus(HttpStatus.CREATED)
	LancamentoResposta adicionar(@UsuarioLogado UUID usuarioId, @PathVariable UUID cartaoId,
			@PathVariable YearMonth mes, @Valid @RequestBody CompraPedido pedido) {
		return LancamentoResposta.de(servico.adicionar(usuarioId, cartaoId, mes, pedido.compra()));
	}

	@PutMapping("/api/lancamentos/{id}")
	LancamentoResposta corrigir(@UsuarioLogado UUID usuarioId, @PathVariable UUID id,
			@Valid @RequestBody CompraPedido pedido) {
		return LancamentoResposta.de(servico.corrigir(usuarioId, id, pedido.compra()));
	}

	@DeleteMapping("/api/lancamentos/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void excluir(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		servico.excluir(usuarioId, id);
	}

}
