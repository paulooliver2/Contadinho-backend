package br.com.contadinho.categorias.controller;

import java.util.List;
import java.util.UUID;

import br.com.contadinho.categorias.domain.Categoria;
import br.com.contadinho.categorias.service.CategoriaServico;
import br.com.contadinho.comum.UsuarioLogado;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** As categorias do dono (HU-15). Criar, renomear e arquivar chegam com o resto da HU-15. */
@RestController
@RequestMapping("/api/categorias")
public class CategoriasController {

	private final CategoriaServico servico;

	public CategoriasController(CategoriaServico servico) {
		this.servico = servico;
	}

	public record CategoriaResposta(
			@NotNull UUID id,
			@NotNull String nome,
			@NotNull Boolean essencial,
			/** Arquivada não aparece nas opções de gasto novo (RN-27). */
			@NotNull Boolean arquivada) {

		public static CategoriaResposta de(Categoria c) {
			return new CategoriaResposta(c.getId(), c.getNome(), c.isEssencial(), c.isArquivada());
		}
	}

	/** Em ordem de nome, arquivadas inclusive. */
	@GetMapping
	public List<CategoriaResposta> listar(@UsuarioLogado UUID usuarioId) {
		return servico.listar(usuarioId).stream().map(CategoriaResposta::de).toList();
	}

}
