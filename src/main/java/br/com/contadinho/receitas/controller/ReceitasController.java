package br.com.contadinho.receitas.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.comum.UsuarioLogado;
import br.com.contadinho.receitas.domain.OcorrenciaReceita;
import br.com.contadinho.receitas.domain.Receita;
import br.com.contadinho.receitas.service.ReceitaServico;
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

/** Receitas e as ocorrências de cada mês (HU-22), e a correção delas (HU-46). */
@RestController
@RequestMapping("/api/receitas")
public class ReceitasController {

	private final ReceitaServico servico;

	public ReceitasController(ReceitaServico servico) {
		this.servico = servico;
	}

	/** HU-22.1. Sem data, vale hoje. Na de todo mês, a data diz o dia e o primeiro mês. */
	public record ReceitaPedido(
			@NotBlank(message = "Informe a descrição.") @Size(max = 80, message = "Use até 80 caracteres.") String descricao,
			@NotNull(message = "Informe o valor.")
			@DecimalMin(value = "0.01", message = "Informe um valor maior que zero.")
			@Digits(integer = 10, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal valor,
			LocalDate data,
			@NotNull(message = "Diga se repete.") Boolean recorrente) {

		public ReceitaPedido {
			descricao = descricao == null ? null : descricao.strip();
		}

		ReceitaServico.Dados dados() {
			return new ReceitaServico.Dados(descricao, valor, data, recorrente);
		}
	}

	public record ReceitaResposta(
			@NotNull UUID id,
			@NotNull String descricao,
			/** O do cadastro; na de todo mês, vale do mês atual em diante. */
			@NotNull BigDecimal valor,
			@NotNull Boolean recorrente,
			@NotNull Integer dia,
			@NotNull YearMonth primeiroMes,
			/** Só a de todo mês: até este dia. */
			LocalDate encerradaEm) {

		public static ReceitaResposta de(Receita r) {
			return new ReceitaResposta(r.getId(), r.getDescricao(), r.getValor(), r.isRecorrente(), r.getDia(),
					r.getPrimeiroMes(), r.getEncerradaEm());
		}
	}

	public record OcorrenciaReceitaResposta(
			@NotNull UUID id,
			@NotNull ReceitaResposta receita,
			@NotNull LocalDate data,
			/** O que conta no mês: o recebido, se confirmado; senão, o previsto. */
			@NotNull BigDecimal valor,
			@NotNull BigDecimal valorPrevisto,
			BigDecimal valorRecebido,
			@NotNull OcorrenciaReceita.Status status) {

		public static OcorrenciaReceitaResposta de(ReceitaServico.ReceitaDoMes r) {
			var o = r.ocorrencia();
			return new OcorrenciaReceitaResposta(o.getId(), ReceitaResposta.de(r.receita()), o.getData(), o.valor(),
					o.getValorPrevisto(), o.getValorRecebido(), r.status());
		}
	}

	public record ReceitasDoMesResposta(
			@NotNull BigDecimal total,
			@NotNull BigDecimal recebido,
			/** Ainda não confirmado: previsto ou a confirmar. */
			@NotNull BigDecimal previsto,
			/** Pela data. */
			@NotNull List<OcorrenciaReceitaResposta> receitas) {

		public static ReceitasDoMesResposta de(List<ReceitaServico.ReceitaDoMes> lista) {
			var receitas = lista.stream().map(OcorrenciaReceitaResposta::de).toList();
			var recebido = BigDecimal.ZERO;
			var previsto = BigDecimal.ZERO;
			for (var r : receitas) {
				if (r.status() == OcorrenciaReceita.Status.RECEBIDA) {
					recebido = recebido.add(r.valor());
				}
				else {
					previsto = previsto.add(r.valor());
				}
			}
			return new ReceitasDoMesResposta(recebido.add(previsto), recebido, previsto, receitas);
		}
	}

	/** HU-22.2. Sem valor, o previsto. */
	public record ConfirmacaoReceitaPedido(
			@DecimalMin(value = "0.01", message = "Informe um valor maior que zero.")
			@Digits(integer = 10, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal valorRecebido) {
	}

	/** As receitas do mês (yyyy-MM), criando as ocorrências que faltam. */
	@GetMapping("/mes/{mes}")
	public ReceitasDoMesResposta doMes(@UsuarioLogado UUID usuarioId, @PathVariable YearMonth mes) {
		return ReceitasDoMesResposta.de(servico.doMes(usuarioId, mes));
	}

	/** HU-22.1. A de uma vez com data até hoje já nasce recebida. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReceitaResposta cadastrar(@UsuarioLogado UUID usuarioId, @Valid @RequestBody ReceitaPedido pedido) {
		return ReceitaResposta.de(servico.cadastrar(usuarioId, pedido.dados()));
	}

	/** HU-46.1. Mudar se repete é 400; receita encerrada é 409. */
	@PutMapping("/{id}")
	public ReceitaResposta alterar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id,
			@Valid @RequestBody ReceitaPedido pedido) {
		return ReceitaResposta.de(servico.alterar(usuarioId, id, pedido.dados()));
	}

	/** Só a de todo mês (a de uma vez é 409): para de gerar a partir do próximo mês. */
	@PostMapping("/{id}/encerrar")
	public ReceitaResposta encerrar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		return ReceitaResposta.de(servico.encerrar(usuarioId, id));
	}

	/** HU-46.1: some com todos os meses dela. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void excluir(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		servico.excluir(usuarioId, id);
	}

	/** HU-22.2: caiu, talvez com outro valor. */
	@PostMapping("/ocorrencias/{id}/confirmacao")
	public OcorrenciaReceitaResposta confirmar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id,
			@Valid @RequestBody ConfirmacaoReceitaPedido pedido) {
		return OcorrenciaReceitaResposta.de(servico.confirmar(usuarioId, id, pedido.valorRecebido()));
	}

	@DeleteMapping("/ocorrencias/{id}/confirmacao")
	public OcorrenciaReceitaResposta desfazer(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		return OcorrenciaReceitaResposta.de(servico.desfazer(usuarioId, id));
	}

}
