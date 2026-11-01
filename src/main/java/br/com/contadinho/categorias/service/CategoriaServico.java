package br.com.contadinho.categorias.service;

import java.util.List;
import java.util.UUID;

import br.com.contadinho.categorias.Categorias;
import br.com.contadinho.categorias.domain.Categoria;
import br.com.contadinho.categorias.repository.CategoriaRepositorio;
import br.com.contadinho.conta.ContaCriada;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** As categorias do dono (HU-15): as padrão chegam com a conta. */
@Service
@Transactional(readOnly = true)
public class CategoriaServico implements Categorias {

	/** HU-15.1, com o "essencial" de cada uma (RN-28). A V5 traz as mesmas para quem já tinha conta. */
	public static final List<Padrao> PADRAO = List.of(
			new Padrao("Moradia", true), new Padrao("Educação", true), new Padrao("Saúde", true),
			new Padrao("Contas", true), new Padrao("Mercado", true), new Padrao("Transporte", true),
			new Padrao("Delivery", false), new Padrao("Lazer", false), new Padrao("Compras", false),
			new Padrao("Assinaturas", false), new Padrao("Viagem", false), new Padrao("Outros", false));

	public record Padrao(String nome, boolean essencial) {
	}

	private final CategoriaRepositorio categorias;

	public CategoriaServico(CategoriaRepositorio categorias) {
		this.categorias = categorias;
	}

	/** Na transação do cadastro: a conta nasce com as categorias ou não nasce. */
	@EventListener
	@Transactional
	public void contaCriada(ContaCriada evento) {
		if (categorias.existsByUsuarioId(evento.usuarioId())) {
			return;
		}
		PADRAO.forEach(p -> categorias.save(new Categoria(evento.usuarioId(), p.nome(), p.essencial())));
	}

	/** Todas, arquivadas inclusive: gastos antigos ainda mostram a delas (RN-27). */
	public List<Categoria> listar(UUID usuarioId) {
		return categorias.findByUsuarioIdOrderByNome(usuarioId);
	}

	@Override
	public boolean podeEscolher(UUID usuarioId, UUID categoriaId) {
		return categorias.findByIdAndUsuarioId(categoriaId, usuarioId).filter(c -> !c.isArquivada()).isPresent();
	}

}
