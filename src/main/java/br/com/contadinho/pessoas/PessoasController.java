package br.com.contadinho.pessoas;

import java.util.List;
import java.util.UUID;

import br.com.contadinho.comum.UsuarioLogado;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pessoas que devem ao dono (HU-16) e o arquivamento delas (HU-42).
 */
@RestController
@RequestMapping("/api/pessoas")
class PessoasController {

	private final PessoaServico servico;

	PessoasController(PessoaServico servico) {
		this.servico = servico;
	}

	/** RN-35. O WhatsApp chega como a pessoa digitou ou como veio do contato e é guardado como +55. */
	record PessoaPedido(
			@NotBlank(message = "Informe o nome.") @Size(max = 80, message = "Use até 80 caracteres.") String nome,
			@Size(max = 40, message = "Use até 40 caracteres.") String apelido,
			@Size(max = 30, message = "Informe DDD e número, como (61) 99999-8888.") String whatsapp) {

		PessoaPedido {
			nome = nome == null ? null : nome.strip();
			apelido = apelido == null ? null : apelido.strip();
		}

		PessoaServico.Dados dados() {
			return new PessoaServico.Dados(nome, apelido, whatsapp);
		}
	}

	record PessoaResposta(
			@NotNull UUID id,
			@NotNull String nome,
			String apelido,
			/** Como +5561999998888. */
			String whatsapp,
			@NotNull Boolean arquivada) {

		static PessoaResposta de(Pessoa p) {
			return new PessoaResposta(p.getId(), p.getNome(), p.getApelido(), p.getWhatsapp(), p.isArquivada());
		}
	}

	/** Em ordem de nome. Sem {@code arquivadas}, só as ativas, que são as das listas de escolha (RN-36). */
	@GetMapping
	List<PessoaResposta> listar(@UsuarioLogado UUID usuarioId,
			@RequestParam(defaultValue = "false") boolean arquivadas) {
		return servico.listar(usuarioId, arquivadas).stream().map(PessoaResposta::de).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PessoaResposta cadastrar(@UsuarioLogado UUID usuarioId, @Valid @RequestBody PessoaPedido pedido) {
		return PessoaResposta.de(servico.cadastrar(usuarioId, pedido.dados()));
	}

	@PutMapping("/{id}")
	PessoaResposta alterar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id,
			@Valid @RequestBody PessoaPedido pedido) {
		return PessoaResposta.de(servico.alterar(usuarioId, id, pedido.dados()));
	}

	@PostMapping("/{id}/arquivar")
	PessoaResposta arquivar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		return PessoaResposta.de(servico.arquivar(usuarioId, id));
	}

	@PostMapping("/{id}/desarquivar")
	PessoaResposta desarquivar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		return PessoaResposta.de(servico.desarquivar(usuarioId, id));
	}

	/** Só sem histórico; com compras ou valores é 409 e o app oferece arquivar (RN-36). */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void excluir(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		servico.excluir(usuarioId, id);
	}

}
