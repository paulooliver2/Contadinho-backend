package br.com.contadinho.receber.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.comum.UsuarioLogado;
import br.com.contadinho.receber.domain.Emprestimo;
import br.com.contadinho.receber.service.EmprestimoServico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Valor emprestado fora do cartão (HU-17). O empréstimo aparece no histórico da pessoa ({@code GET
 * /api/receber/pessoas/{pessoaId}}).
 */
@RestController
@RequestMapping("/api/receber/emprestimos")
public class EmprestimosController {

	private final EmprestimoServico servico;

	public EmprestimosController(EmprestimoServico servico) {
		this.servico = servico;
	}

	/** HU-17.1. Sem {@code primeiraDevolucao} é "Sem data" (RN-32). */
	public record EmprestimoPedido(
			@NotNull(message = "Escolha a pessoa.") UUID pessoaId,
			@Size(max = 80, message = "Use até 80 caracteres.") String descricao,
			@NotNull(message = "Informe o valor.")
			@DecimalMin(value = "0.01", message = "Informe um valor maior que zero.")
			@Digits(integer = 10, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal valor,
			@NotNull(message = "Escolha 1, 2, 3 ou 6 parcelas.") Integer parcelas,
			LocalDate primeiraDevolucao) {

		public EmprestimoPedido {
			descricao = descricao == null ? null : descricao.strip();
		}

		public EmprestimoServico.Dados dados() {
			return new EmprestimoServico.Dados(pessoaId, descricao, valor, parcelas, primeiraDevolucao);
		}
	}

	public record ParcelaResposta(@NotNull Integer numero, @NotNull BigDecimal valor, LocalDate vencimento) {
	}

	public record EmprestimoResposta(
			@NotNull UUID id,
			@NotNull UUID pessoaId,
			String descricao,
			@NotNull BigDecimal valor,
			@NotNull List<ParcelaResposta> parcelas) {

		public static EmprestimoResposta de(Emprestimo e) {
			return new EmprestimoResposta(e.getId(), e.getPessoaId(), e.getDescricao(), e.getValor(), e.dividir()
				.stream()
				.map(p -> new ParcelaResposta((int) p.numero(), p.valor(), p.vencimento()))
				.toList());
		}
	}

	/** Pessoa arquivada ou de outro dono é 400 no campo pessoaId (RN-36). */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public EmprestimoResposta registrar(@UsuarioLogado UUID usuarioId, @Valid @RequestBody EmprestimoPedido pedido) {
		return EmprestimoResposta.de(servico.registrar(usuarioId, pedido.dados()));
	}

	/** Apaga o empréstimo e as parcelas; pagamentos já feitos continuam com a pessoa. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void excluir(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		servico.excluir(usuarioId, id);
	}

}
