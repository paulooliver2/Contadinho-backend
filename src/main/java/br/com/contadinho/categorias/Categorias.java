package br.com.contadinho.categorias;

import java.util.UUID;

/** O que os outros módulos perguntam sobre as categorias do dono. */
public interface Categorias {

	/** Do dono e não arquivada: pode ir num gasto novo (RN-27). */
	boolean podeEscolher(UUID usuarioId, UUID categoriaId);

}
