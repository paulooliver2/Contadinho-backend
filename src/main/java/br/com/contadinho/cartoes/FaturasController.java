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
 * A fatura de cada mês, as compras digitadas nela (HU-47) e de quem é cada compra (HU-08). O mês é o do vencimento,
 * como 2026-11 (RN-58).
 */
@RestController
class FaturasController {

	private final FaturaServico servico;

	private final ResponsavelServico responsaveis;

	FaturasController(FaturaServico servico, ResponsavelServico responsaveis) {
		this.servico = servico;
		this.responsaveis = responsaveis;
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
			@NotNull Lancamento.Origem origem,
			/** De quem é a compra; sem pessoa, é do dono (RN-15). */
			UUID pessoaId) {

		static LancamentoResposta de(Lancamento l) {
			return new LancamentoResposta(l.getId(), l.getData(), l.getDescricao(), l.getValor(), l.getParcelaAtual(),
					l.getParcelaTotal(), l.getFinalCartao(), l.getOrigem(), l.getPessoaId());
		}
	}

	/**
	 * HU-08. Sem {@code pessoaId}, a compra volta para o dono. {@code proximas}: as próximas parcelas da mesma compra
	 * também são dessa pessoa (RN-17).
	 */
	record ResponsavelPedido(UUID pessoaId, Boolean proximas) {
	}

	/** {@code proximas}: parcelas já gravadas em faturas seguintes que mudaram junto. */
	record AtribuicaoResposta(@NotNull LancamentoResposta lancamento, @NotNull Integer proximas) {
	}

	record FaturaResposta(
			@NotNull String mes,
			@NotNull LocalDate fechamento,
			@NotNull LocalDate vencimento,
			@NotNull Boolean aberta,
			@NotNull BigDecimal total,
			/** HU-10: a soma das partes é sempre o total. */
			@NotNull List<ParteResposta> partes,
			@NotNull List<LancamentoResposta> lancamentos) {
	}

	/** Sem {@code pessoaId}, é a parte do dono (RN-15). */
	record ParteResposta(UUID pessoaId, @NotNull BigDecimal total, @NotNull Integer quantidade) {
	}

	@GetMapping("/api/cartoes/{cartaoId}/faturas/{mes}")
	FaturaResposta ver(@UsuarioLogado UUID usuarioId, @PathVariable UUID cartaoId, @PathVariable YearMonth mes) {
		var vista = servico.ver(usuarioId, cartaoId, mes);
		var ciclo = vista.ciclo();
		return new FaturaResposta(ciclo.mes().toString(), ciclo.fechamento(), ciclo.vencimento(), vista.aberta(),
				vista.total(),
				vista.partes().stream().map(p -> new ParteResposta(p.pessoaId(), p.total(), p.quantidade())).toList(),
				vista.lancamentos().stream().map(LancamentoResposta::de).toList());
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

	@PutMapping("/api/lancamentos/{id}/responsavel")
	AtribuicaoResposta atribuir(@UsuarioLogado UUID usuarioId, @PathVariable UUID id,
			@RequestBody ResponsavelPedido pedido) {
		var atribuicao = responsaveis.atribuir(usuarioId, id, pedido.pessoaId(), Boolean.TRUE.equals(pedido.proximas()));
		return new AtribuicaoResposta(LancamentoResposta.de(atribuicao.lancamento()), atribuicao.proximas());
	}

	@DeleteMapping("/api/lancamentos/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void excluir(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		servico.excluir(usuarioId, id);
	}

}
